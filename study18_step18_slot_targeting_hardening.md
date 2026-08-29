# Study 18 - 지면 타겟팅을 하드 제약으로 분리하기

## 1. 이번 단계에서 배운 핵심

광고의 사용자 관심사와 노출 지면은 둘 다 문자열 목록처럼 보이지만 실패 허용도가 다르다.

- 관심사 매칭 실패: 추천 정밀도가 낮아질 수 있으나 fallback 가능
- 지면 매칭 실패: 계약하지 않은 위치에 광고가 노출되는 correctness 위반

따라서 지면은 점수나 소프트 matcher의 한 조건이 아니라, 후보 집합에 들어오기 전에 적용하는 하드 제약이어야 한다.

## 2. 변경 이전에는 왜 위험했는가

변경 전 모델:

```text
interestTags = [fashion, beauty]
```

이 값으로 사용자 관심사도 비교하고 요청 `slotId`도 비교했다. 정상 경로에서 우연히 잘 동작해도 DMP 장애나 타겟 불일치 fallback이 원본 후보를 사용하면 지면 조건이 사라졌다.

```text
ACTIVE top 200
-> profile matcher
-> 실패하면 raw candidates
```

이 문제는 matcher 조건 한 줄을 더 넣는 것으로 충분하지 않다. fallback이 그 matcher를 우회할 수 있기 때문이다.

## 3. 변경 이후의 불변식

Step 18은 다음 불변식을 코드 구조로 만들었다.

```text
AdServingService가 선택할 수 있는 모든 후보는
requestedSlot과 일치하거나 ALL_SLOTS(*)여야 한다.
```

이를 두 단계에서 보장한다.

1. Elasticsearch가 `slotIds IN [requestedSlot, *]`로 후보를 줄인다.
2. `AdSlotMatcher`가 애플리케이션 경계에서 다시 검사한다.

검색 필터는 성능을 담당하고 서비스 필터는 안전을 담당한다. ES 문서가 오래됐거나 repository 구현이 바뀌어도 서비스의 불변식은 유지된다.

## 4. 사용자 타겟팅과 지면 타겟팅의 분리

변경 전:

```text
DefaultAdMatcher
  = 성별 + 지역 + 관심사 + 지면
```

변경 후:

```text
AdSlotMatcher
  = 지면 하드 제약

DefaultAdMatcher
  = 성별 + 지역 + 관심사 소프트 타겟팅
```

이 분리는 SRP만을 위한 리팩터링이 아니다. fallback 시 어떤 규칙을 절대 완화하면 안 되는지를 타입과 호출 순서로 표현한 것이다.

## 5. 캐시는 쿼리 의미를 키에 포함해야 한다

변경 전 캐시 키는 사실상 상수였다.

```text
cache = ACTIVE top 200
```

지면 필터가 쿼리에 들어간 뒤에도 캐시를 하나만 쓰면 쿼리 결과의 의미가 섞인다. 그래서 변경 후에는 정규화된 `slotId`를 캐시 키로 사용한다.

```text
cache[home]    = home + * candidates
cache[fashion] = fashion + * candidates
```

일반화하면 캐시 키는 결과를 바꾸는 모든 입력을 포함해야 한다.

## 6. 호환성과 엄격성의 트레이드오프

기존 광고에는 지면 값이 없다. 이를 즉시 fail-closed 처리하면 재색인 직후 모든 기존 광고가 검색에서 사라질 수 있다.

이번 단계에서는 null/blank를 `*`로 매핑해 전환 안정성을 선택했다.

변경 전과 배포 직후:

```text
legacy targetSlotIds=null -> 모든 지면 허용(*)
```

최종 운영 목표:

```text
legacy row backfill -> 실제 허용 지면 명시
```

즉 `*`는 영구적인 모델의 기본값이라기보다 migration bridge다. 안전한 배포와 최소 권한 원칙 사이의 단계적 전환이다.

## 7. 테스트에서 확인한 것

단순 matcher 단위 테스트만으로는 충분하지 않았다. 다음 층을 각각 검증했다.

- Mapper: 관심사와 지면이 서로 섞이지 않는가
- JPA: 신규 컬럼이 DB 왕복 후 유지되는가
- ES repository: keyword `In` 쿼리가 실제 컨테이너에서 동작하는가
- Cache: 같은 지면은 재사용하고 다른 지면은 분리하는가
- Serving: 정상/DMP 실패/타겟 불일치 모든 경로에서 지면 불변식이 유지되는가

집중 테스트 22개와 전체 통합 테스트 72개가 통과했다.

## 8. 변경 전/후 한 줄 비교

```text
Before: 모든 광고를 가져온 뒤 일부 정상 경로에서만 지면을 검사했다.
After : 지면에 맞는 후보만 가져오고, 모든 선택 경로 직전에 다시 검사한다.
```

## 9. 다른 시스템에 적용할 원칙

결제 통화, 테넌트 ID, 데이터 거주 지역, 권한 scope처럼 위반 시 correctness나 보안 문제가 되는 조건은 점수화하거나 fallback에서 완화하면 안 된다.

```text
하드 제약으로 후보 집합을 먼저 제한
-> 소프트 조건으로 순위와 개인화
-> fallback도 제한된 후보 안에서만 수행
```

Step 18의 가장 중요한 학습은 Elasticsearch 사용법이 아니라, 실패 시에도 완화하면 안 되는 도메인 경계를 실행 흐름 앞단에 고정하는 방법이다.
