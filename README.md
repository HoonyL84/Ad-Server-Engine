# Ad Server Engine

대규모 트래픽 환경에서 광고 응답 지연과 외부 의존성(DMP) 리스크를 줄이기 위해,  
실시간 광고 서빙 구조를 단계적으로 설계·구현·검증한 프로젝트입니다.

---

## Why This Project

- DMP 지연이 전체 광고 응답 지연으로 전파되는 문제를 줄입니다.
- 개인화 정확도와 응답 안정성의 균형점을 수치로 검증합니다.
- fallback 기반으로 장애 상황에서도 광고 응답이 끊기지 않도록 설계합니다.

---

## Engineering Milestones

### Step 1. Foundation
- Java 21, Virtual Thread 기반 실행 환경 구성
- Docker Compose 기반 MySQL/Redis/Elasticsearch 개발 환경 구성

### Step 2. DMP Contract
- gRPC(Protobuf) 인터페이스 정의
- 광고 서버와 프로필 조회 경계 분리

### Step 3. Domain Modeling
- Advertiser/Ad 2-Tier 모델 정리
- 성별/지역/관심사 타겟 필드 구성
- 로컬 검증용 시딩 데이터 구성

### Step 4. Search Synchronization
- MySQL 엔티티와 ES 문서 모델 분리
- AFTER_COMMIT 기반 비동기 색인
- MySQL → ES Bulk Sync API 추가

### Step 5. Serving Orchestrator
- DMP 조회와 ES 후보 조회 병렬 실행
- DMP timeout(30ms) + fallback 응답 경로 구성
- fallback reason 분리(`PROFILE_NOT_FOUND`, `DMP_TIMEOUT`, `TARGET_NOT_MATCHED`, `NO_CANDIDATE`)
- Step5에서 계약/분기 구조를 먼저 고정하고, 이후 실제 gRPC 호출 경로를 보강해 재검증

### Step 6. Matching & Ranking Boundary
- L1 후보 조회, L2 매칭, 최종 선택 책임 분리
- 후보 조회 timeout/error를 별도 fallback reason으로 확장

### Step 7. Budget Control
- Redis Lua 기반 원자적 예산 차감
- 예산 소진 광고 차단 및 `BUDGET_EXHAUSTED` fallback 처리

### Step 8. Performance Baseline
- k6 기반 3개 지면 부하 테스트
- candidate cache / executor 재사용 / 경로 병목 분리
- 로컬 단일 인스턴스 기준 성능 관측값 확보

### Step 9. Observability Baseline
- Prometheus 메트릭 노출(`latency`, `fallback reason`, `timeout`)
- Grafana 대시보드 및 Alert Rule 구성

### Step 10. Traceability Baseline
- `X-Trace-Id` 전파
- MDC 컨텍스트 전달로 병렬 구간 추적
- fallback reason + trace 상관관계 확인

---

## Key Features

- Parallel Serving
- Timeout & Fallback
- Failure Classification
- Multi-stage Filtering
- Budget Guard with Redis Lua
- Performance Baseline with k6
- Observability (Prometheus/Grafana)
- Traceability (Trace ID + MDC)

---

## Blog Series

- Vol 1: [서비스 패턴 분석](https://velog.io/@hoonyl/1.-%EB%8B%B9%EA%B7%BC-%EB%AC%B4%EC%8B%A0%EC%82%AC-%EC%98%A4%EB%8A%98%EC%9D%98%EC%A7%91-%EA%B4%91%EA%B3%A0%EB%A5%BC-%EC%A7%81%EC%A0%91-%EB%B6%84%EC%84%9D%ED%95%98%EB%A9%B0-%EC%84%A4%EA%B3%84%EC%9D%98-%EA%B7%BC%EA%B1%B0%EB%A5%BC-%EC%B0%BE%EB%8B%A4)
- Vol 2-1: [데이터 모델링](https://velog.io/@hoonyl/2-1.-%EA%B3%A0%EC%84%B1%EB%8A%A5-%EC%84%9C%EB%B9%94-%EC%9C%84%ED%95%9C-%EB%8D%B0%EC%9D%B4%ED%84%B0-%EB%AA%A8%EB%8D%B8%EB%A7%81)
- Vol 2-2: [서빙 실행 구조](https://velog.io/@hoonyl/2-2.-%EC%84%9C%EB%B9%99-%EC%86%8D%EB%8F%84%EB%A5%BC-%EB%81%8C%EC%96%B4%EC%98%AC%EB%A6%AC%EB%8A%94-%EC%8B%A4%ED%96%89-%EA%B5%AC%EC%A1%B0)
- Vol 3: [최적화 관점](https://velog.io/@hoonyl/3.-%EA%B4%91%EA%B3%A0-%EC%97%94%EC%A7%84-%EC%B5%9C%EC%A0%81%ED%99%94%EC%9D%98-%EB%B3%B8%EC%A7%88)
- Vol 4: [MySQL-ES 동기화](https://velog.io/@hoonyl/4.-MySQL%EA%B3%BC-Elasticsearch%EB%A5%BC-%EC%95%88%EC%A0%84%ED%95%98%EA%B2%8C-%EB%8F%99%EA%B8%B0%ED%99%94%ED%95%98%EA%B8%B0)
- Vol 5: [멈추지 않는 서빙 흐름](https://velog.io/@hoonyl/5.-%EB%A9%88%EC%B6%94%EC%A7%80-%EC%95%8A%EB%8A%94-%EA%B4%91%EA%B3%A0-%EC%84%9C%EB%B9%99-%ED%9D%90%EB%A6%84-%EB%A7%8C%EB%93%A4%EA%B8%B0)
- Vol 6: [선택 로직 분리](https://velog.io/@hoonyl/6.-%EA%B4%91%EA%B3%A0-%EC%84%A0%ED%83%9D-%EB%A1%9C%EC%A7%81%EC%9D%84-%EB%82%98%EB%88%88-%EC%9D%B4%EC%9C%A0)
- Vol 7: [예산 소진 방어](https://velog.io/@hoonyl/7.-%EC%98%88%EC%82%B0%EC%9D%B4-%EC%97%86%EB%8A%94-%EA%B4%91%EA%B3%A0%EB%A5%BC-%EB%A7%89%EB%8A%94-%ED%9D%90%EB%A6%84-%EB%A7%8C%EB%93%A4%EA%B8%B0)
- Vol 8: [부하 테스트에서 먼저 분리한 것](https://velog.io/@hoonyl/8.-%EB%B6%80%ED%95%98-%ED%85%8C%EC%8A%A4%ED%8A%B8%EC%97%90%EC%84%9C-%EB%A8%BC%EC%A0%80-%EB%B6%84%EB%A6%AC%ED%95%9C-%EA%B2%83)
- Vol 9: [지표를 계속 볼 수 있게 만들기](https://velog.io/@hoonyl/9.-%EB%B6%80%ED%95%98-%ED%85%8C%EC%8A%A4%ED%8A%B8-%EC%A7%80%ED%91%9C%EB%A5%BC-%EA%B3%84%EC%86%8D-%EB%B3%BC-%EC%88%98-%EC%9E%88%EA%B2%8C-%EB%A7%8C%EB%93%A4%EA%B8%B0)
- Vol 10: [문제가 보였을 때 요청을 따라갈 수 있게 만들기](https://velog.io/@hoonyl/10.-%EB%AC%B8%EC%A0%9C%EA%B0%80-%EB%B3%B4%EC%98%80%EC%9D%84-%EB%95%8C-%EC%9A%94%EC%B2%AD%EC%9D%84-%EB%94%B0%EB%9D%BC%EA%B0%88-%EC%88%98-%EC%9E%88%EA%B2%8C-%EB%A7%8C%EB%93%A4%EA%B8%B0)

---

## Tech Stack

- Java 21, Spring Boot 3.4
- MySQL, Redis, Elasticsearch
- gRPC (Protobuf 3)
- Docker Compose
- Prometheus, Grafana, k6

