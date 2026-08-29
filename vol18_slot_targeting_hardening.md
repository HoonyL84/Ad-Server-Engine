# 18. Elasticsearch를 넣고도 다른 지면 광고가 나갈 수 있었던 이유

광고 서버에 Elasticsearch를 붙이면 후보 검색은 빨라진다. 하지만 검색이 빠르다는 것과 올바른 광고가 노출된다는 것은 다른 문제였다.

이번 단계에서는 `home` 지면 요청에 `fashion` 광고가 섞일 수 있던 구조를 바로잡았다.

---

## 변경 전: 관심사와 지면이 한 필드에 있었다

처음 구현은 `interestTags` 하나에 두 의미를 넣었다.

```text
fashion = 사용자 관심사
fashion = 광고 지면
```

Elasticsearch에서는 모든 ACTIVE 광고 중 입찰가 상위 후보를 가져왔고, 애플리케이션 matcher가 관심사와 지면을 함께 확인했다.

```text
slotId=home 요청
-> ACTIVE 광고 top 200
-> DMP 사용자 프로필 조회
-> matcher
-> 최고 입찰가 선택
```

정상 경로만 보면 동작하는 것처럼 보였다. 문제는 fallback이었다.

## 실제 실패 가능 시나리오

후보가 아래와 같다고 가정한다.

| 광고 | 지면 | 입찰가 |
|---|---|---:|
| 패션 광고 | fashion | 5,000 |
| 가구 광고 | home | 2,000 |

DMP가 timeout되거나 사용자 타겟이 모두 불일치하면 원본 후보 중 최고 입찰가를 선택하는 fallback이 있었다. 이때 `home` 지면에서도 5,000원짜리 패션 광고가 이길 수 있었다.

```text
Before
전체 후보 -> 프로필 조회 실패 -> raw 후보 fallback -> 패션 광고 선택 가능
```

추천 품질 문제가 아니라 광고주가 지정하지 않은 위치에 광고가 나가는 문제였다.

---

## 변경 후: 지면은 절대 완화하지 않는 조건이 됐다

먼저 데이터 모델을 분리했다.

```text
interestTags     = 사용자 관심사
targetSlotIds    = 광고가 허용된 지면
```

RDB에는 `target_slot_ids`, Elasticsearch 문서에는 keyword 배열 `slotIds`를 추가했다. 모든 지면에 노출 가능한 광고는 `*`로 명시한다.

후보 검색도 바뀌었다.

```text
After
slotId=home
-> ES에서 status=ACTIVE AND slotIds IN [home, *]
-> AdSlotMatcher로 다시 검증
-> DMP 조회와 사용자 타겟팅
-> 어떤 fallback도 home 후보 안에서만 실행
```

이제 DMP가 실패해도 사용자 개인화만 포기할 뿐 지면 제약은 포기하지 않는다.

## 왜 두 번 검사했는가

ES에서 이미 지면을 필터링하는데 서비스에서 다시 검사하는 이유가 있다.

- ES 필터: 전체 광고를 애플리케이션으로 가져오지 않기 위한 성능 장치
- 서비스 필터: 색인 지연이나 repository 변경에도 지면 위반을 막는 안전 장치

검색 계층 하나를 완전히 신뢰하기보다 도메인 경계에서 불변식을 한 번 더 확인하는 defense-in-depth 구조다.

## 캐시도 지면별로 나눴다

변경 전에는 후보 캐시가 하나였다.

```text
fashion 조회 결과 저장
-> 짧은 시간 뒤 home 요청이 같은 캐시를 볼 가능성
```

변경 후에는 `slotId`가 캐시 키다.

```text
cache[fashion]
cache[home]
cache[local]
```

쿼리 조건이 달라졌다면 캐시 키도 달라져야 한다는 기본 원칙을 적용했다.

## 기존 데이터는 어떻게 처리했는가

기존 광고에는 지면 값이 없다. 배포 순간 이를 모두 차단하면 광고가 사라질 수 있으므로, null/blank 값은 재색인할 때 `*`로 변환한다.

다만 이는 호환을 위한 임시 정책이다. 운영 전환에서는 기존 광고를 실제 지면으로 backfill하고 전체 재색인을 수행해야 한다.

```text
RDB 컬럼 추가
-> 기존 데이터 backfill
-> 애플리케이션 배포
-> ES 전체 재색인
-> 지면별 후보와 fallback 지표 확인
```

코드만 배포하고 재색인을 생략하면 기존 ES 문서에 `slotIds`가 없어 검색 결과가 비어 버릴 수 있다.

## 검증

단위 테스트만 통과시키지 않고 실제 인프라를 함께 띄웠다.

```powershell
.\gradlew.bat test --rerun-tasks --no-daemon
```

MySQL, Redis, Elasticsearch, Kafka, 로컬 DMP 시나리오를 포함한 전체 72개 테스트가 통과했다.

특히 다음을 확인했다.

- `targetSlotIds`의 DB 저장/조회
- Elasticsearch 지면 쿼리
- 지면별 캐시 격리
- DMP 장애 fallback의 지면 안전성
- 타겟 불일치 fallback의 지면 안전성
- 기존 광고의 `*` 호환 매핑

## 마무리

변경 전 Elasticsearch는 ACTIVE 광고를 빠르게 모으는 후보 검색 PoC에 가까웠다. 변경 후에는 요청 지면별로 후보를 줄이는 명확한 역할을 갖게 됐고, 서비스 계층은 어떤 실패 경로에서도 그 경계를 지킨다.

이번 개선으로 설명할 수 있는 핵심은 “Elasticsearch를 사용했다”가 아니다.

```text
검색 성능을 위해 ES에서 1차 필터링하고,
정합성을 위해 서비스에서 2차 검증했으며,
fallback도 하드 제약 안에서만 동작하도록 설계했다.
```

이제 지면이 추가되면 Elasticsearch 도입의 의미도 함께 커진다. 전체 광고를 매번 읽는 대신 지면에 맞는 후보만 빠르게 좁히면서도, 장애 상황의 잘못된 노출까지 막기 때문이다.
