# Step 18 Implementation History - 지면 타겟팅 경계 강화

## 목적

Step 18은 사용자 타겟팅과 광고 지면 타겟팅을 분리하고, 정상 경로뿐 아니라 프로필 조회 실패와 타겟 불일치 fallback에서도 다른 지면의 광고가 노출되지 않도록 서빙 경계를 강화한 단계다.

이 문서는 이전 구현과 변경 후 구현을 함께 기록한다. 과거 Step 문서는 당시 판단을 보존하고, 최종 보완 내용은 Step 18에만 남긴다.

## 1. 변경 전 구조와 문제

### 1-1. 하나의 필드가 두 의미를 가졌다

변경 전에는 `interestTags`가 다음 두 역할을 동시에 맡았다.

- 사용자의 관심사와 광고를 비교하는 소프트 타겟팅 조건
- `fashion`, `home`, `local` 같은 광고 지면을 구분하는 하드 노출 조건

```text
Ad.targetInterestTags
  -> Elasticsearch interestTags
  -> 사용자 관심사 매칭
  -> 동시에 slotId 매칭에도 사용
```

이 구조에서는 `fashion`이 사용자의 관심사인지 광고가 노출될 지면인지 타입만 보고 구분할 수 없었다.

### 1-2. ES 후보 조회가 지면을 제한하지 않았다

변경 전 후보 조회는 모든 `ACTIVE` 광고 중 입찰가 상위 200개를 가져왔다.

```text
요청 slotId=home
-> ES: status=ACTIVE, bid DESC, top 200
-> fashion/local/home 후보가 모두 애플리케이션으로 유입
```

따라서 지면 제약은 ES가 아니라 후단 matcher와 fallback 구현의 정확성에만 의존했다.

### 1-3. fallback이 지면 경계를 우회했다

정상 프로필 경로에서는 matcher가 동작했지만, 다음 경로는 원본 후보를 입찰가순으로 선택할 수 있었다.

```text
DMP 프로필 없음/timeout/circuit-open -> rawCandidates 중 최고 입찰가
사용자 타겟 불일치             -> rawCandidates 중 최고 입찰가
```

예를 들어 `home` 지면 요청에 아래 후보가 들어오면:

| 광고 | 지면 | 입찰가 |
|---|---|---:|
| A | fashion | 5,000 |
| B | home | 2,000 |

DMP 장애나 타겟 불일치 fallback에서 A가 선택될 수 있었다. 이는 단순 추천 품질 저하가 아니라 광고 계약의 노출 위치를 위반하는 correctness 문제다.

### 1-4. 캐시도 지면을 구분하지 않았다

후보 캐시가 전역 1개였기 때문에 먼저 조회된 지면의 후보가 짧은 TTL 동안 다른 지면 요청에 재사용될 가능성이 있었다.

```text
fashion 요청 -> 전역 캐시 저장
home 요청    -> 같은 캐시 재사용 가능
```

## 2. 변경 후 데이터 모델

| 구분 | 변경 전 | 변경 후 |
|---|---|---|
| RDB 광고 지면 | 전용 컬럼 없음 | `ad.target_slot_ids` |
| ES 광고 지면 | `interestTags`에 혼재 | keyword 배열 `slotIds` |
| 전체 지면 허용 | 암묵적/불명확 | 명시적 `*` |
| 사용자 관심사 | `interestTags` | `interestTags` 유지 |
| 지면 제약 | matcher 일부 경로 | ES 선필터 + 서비스 방어 필터 |

`AdEventPayload`에도 `targetSlotIds`를 추가해 RDB 변경이 Outbox/색인 경로를 거쳐 ES 문서까지 전달된다. `AdDocumentMapper`는 관심사와 지면을 별도로 파싱하고 소문자·중복 제거를 적용한다.

기존 데이터의 `target_slot_ids`가 비어 있으면 재색인 시 `*`로 변환한다. 이는 배포 직후 기존 광고가 모두 사라지는 것을 막기 위한 호환 정책이다.

## 3. 변경 후 실행 흐름

```text
GET /ads/serve?slotId=home
-> slotId 정규화 및 빈 값 거부
-> ES: status=ACTIVE AND slotIds IN [home, *]
-> home 전용 후보 캐시
-> AdSlotMatcher로 한 번 더 [home, *]만 통과
-> DMP 프로필 조회
   -> 성공: DefaultAdMatcher로 사용자 관심사/성별/지역 매칭
   -> 타겟 불일치: 이미 home으로 제한된 후보 안에서 fallback
   -> timeout/circuit-open/not-found: 이미 home으로 제한된 후보 안에서 fallback
-> 최고 입찰가 광고 선택
```

핵심은 지면 필터가 사용자 프로필보다 먼저 적용된다는 점이다. DMP의 성공 여부와 관계없이 모든 후속 경로는 지면 안전 후보 집합 안에서만 움직인다.

## 4. 코드별 변경

### 도메인과 이벤트

- `Ad`: `targetSlotIds` 영속 필드 추가
- `AdEventPayload`: 색인 이벤트에 지면 정보 포함
- `AdDataSeeder`: persona별 `fashion`, `local`, `home`, `*` 저장

### 검색 계층

- `AdDocument`: keyword 타입 `slotIds` 추가
- `AdDocumentMapper`: `targetInterestTags`와 `targetSlotIds`를 독립 매핑
- `AdSearchRepository`: `status AND slotIds IN (...)` 쿼리 추가
- `DefaultAdCandidateSearchService`: 요청 지면과 `*`만 ES에서 조회하고 캐시를 지면별로 격리

### 서빙 계층

- `AdSlotMatcher`: 지면 하드 제약 전용 인터페이스
- `DefaultAdSlotMatcher`: 정확히 일치하거나 `*`인 광고만 허용
- `DefaultAdMatcher`: 사용자 타겟팅만 담당하도록 역할 축소
- `AdServingService`: 후보 조회 직후 지면 필터를 적용해 모든 fallback의 입력을 안전하게 제한

## 5. 변경 전/후 실패 시나리오 비교

### DMP 장애

변경 전:

```text
전체 ACTIVE 후보 -> DMP timeout -> 전체 후보 중 최고 입찰가
결과: 다른 지면 광고 선택 가능
```

변경 후:

```text
ES 지면 필터 -> 서비스 지면 필터 -> DMP timeout -> 같은 지면 후보 중 최고 입찰가
결과: 사용자 정밀 타겟팅은 완화되지만 지면 계약은 유지
```

### 사용자 타겟 불일치

변경 전에는 matcher 결과가 비면 원본 후보로 돌아갈 수 있었다. 변경 후에는 지면 안전 후보를 fallback 후보로 사용하므로 다른 지면으로 되돌아갈 수 없다.

### 캐시 재사용

변경 전에는 단일 캐시가 지면 사이에 공유됐다. 변경 후 캐시 키가 정규화된 `slotId`이므로 `fashion`의 캐시가 `home`에 사용되지 않는다.

## 6. 마이그레이션과 재색인

로컬 PoC는 Hibernate `ddl-auto=update`로 `target_slot_ids` 컬럼을 추가할 수 있다. 운영 환경에서는 명시적 DB migration이 필요하다.

안전한 전환 순서는 다음과 같다.

1. RDB에 `target_slot_ids` 컬럼을 추가한다.
2. 기존 광고를 실제 지면 값 또는 임시 `*`로 backfill한다.
3. 애플리케이션을 배포한다.
4. 전체 광고를 bulk reindex한다.
5. ES 문서에 `slotIds`가 채워졌는지 확인한다.
6. 신규 지면 필터 쿼리의 빈 결과율과 fallback 비율을 관찰한다.

기존 ES 문서는 `slotIds`가 없으므로 애플리케이션 코드만 배포하고 재색인하지 않으면 후보가 누락될 수 있다. 재색인은 선택 사항이 아니라 전환 절차의 일부다.

## 7. 검증 결과

### 집중 테스트

지면 matcher, 문서 mapper, 후보 검색, 캐시 격리, stale cache, 서빙 fallback 테스트 22개가 통과했다.

### 전체 통합 테스트

```powershell
.\gradlew.bat test --rerun-tasks --no-daemon
```

MySQL, Redis, Elasticsearch, Kafka와 로컬 DMP 시나리오를 포함한 전체 72개 테스트가 통과했다.

검증한 핵심 회귀 조건:

- RDB에 `targetSlotIds` 저장 및 조회
- ES keyword 배열과 `In` 쿼리의 실제 동작
- `home` 요청에서 `fashion` 광고 제외
- DMP 프로필 없음/장애 fallback에서도 다른 지면 광고 제외
- 사용자 타겟 불일치 fallback에서도 다른 지면 광고 제외
- 동일 지면 캐시 재사용과 서로 다른 지면 캐시 격리
- 기존 null/blank 지면 데이터의 `*` 호환 매핑

## 8. 남은 운영 고려사항

- `*`는 무중단 호환에는 유리하지만 장기적으로는 지나치게 넓다. 운영 migration에서는 가능한 한 명시적 지면으로 backfill해야 한다.
- 동적 `slotId`가 무제한으로 들어오면 지면별 캐시 키의 cardinality가 커질 수 있다. 운영 API gateway 또는 지면 registry에서 허용 지면을 제한하는 것이 좋다.
- 현재 다중 지면은 comma-separated RDB 문자열이다. 지면별 리포팅과 변경 이력이 중요해지면 별도 연관 테이블을 검토할 수 있다.

## 최종 판단

Step 18 이전의 Elasticsearch는 빠른 후보 검색 PoC였지만 지면 경계를 완전히 책임지지 못했다. Step 18 이후에는 ES가 검색 범위를 줄이고 서빙 서비스가 동일 규칙을 다시 검증하는 defense-in-depth 구조가 됐다. 이로써 Elasticsearch 도입 이유도 단순 기술 시연이 아니라, 지면별 대량 후보 축소와 안전한 fallback을 함께 설명할 수 있게 됐다.
