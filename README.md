# Ad Server Engine

트래픽 증가 상황에서 광고 응답 지연과 외부 의존성 문제를 줄이기 위해,  
실시간 광고 서빙 구조를 설계하고 구현하는 프로젝트입니다.

---

## Why This Project?

본 프로젝트는 광고 서빙 과정에서 발생할 수 있는 지연과 장애 전파를 줄이는 과정을 기록합니다.

- 외부 의존성(DMP) 지연이 전체 광고 응답 지연으로 이어지는 문제를 해결합니다.
- 개인화 정확도보다 응답 안정성을 우선하는 서빙 전략을 검증합니다.
- fallback 기반의 안정적인 광고 서빙 구조를 설계하고 구현합니다.

---

## Engineering Milestones

### Phase 1: 개발 준비 및 도메인 구축

#### 1. Core Environment (Step 1)
- **Java 21 & Virtual Threads**: I/O 바운드 작업 최적화를 위한 최신 런타임 환경 구축
- **Infrastructure**: Docker Compose 기반의 MySQL, Redis, Kafka 핵심 인프라 셋업

#### 2. Interface Definition (Step 2)
- **DMP Integration**: gRPC(Protobuf 3)를 활용한 외부 유저 프로필 연동 규격 정의
- **장애 격리**: 외부 의존성과의 명확한 도메인 경계 설정

#### 3. Domain Readiness (Step 3)
- **2-Tier Modeling**: 서빙 시점의 Join 제거를 위한 Advertiser-Ad 2계층 구조 확립
- **Hybrid Targeting**: 성별, 지역(계층형 ID), 관심사별 고속 필터링을 위한 스키마 설계
- **Development Seeding**: 로컬 검증을 위한 3,000건 이상의 광고 데이터 적재 환경 구성
- **Mock DMP Infrastructure**: 유저 프로필 조회 시뮬레이션을 위한 100,000명의 유저 데이터 구성

#### 4. Search Synchronization (Step 4)
- **AdDocument Mapping**: Elasticsearch 전용 문서 모델과 검색 리포지토리 정의
- **Event-driven Indexing**: 광고 생성 트랜잭션 커밋 이후 ES 비동기 색인 처리
- **Bulk Sync API**: 초기 정합성 확보를 위한 MySQL->Elasticsearch 일괄 동기화 API 제공

#### 5. Serving Orchestration (Step 5)
- **Parallel Lookup**: DMP 유저 프로필 조회와 Elasticsearch 광고 후보 조회를 Virtual Thread 기반으로 병렬 실행
- **DMP Timeout & Fallback**: 유저 프로필 조회에 기본 30ms 타임아웃을 두고, 초과/실패 시 광고 후보 기반 fallback 응답 제공
- **Fallback Reason 분리**: `PROFILE_NOT_FOUND`, `DMP_TIMEOUT`, `TARGET_NOT_MATCHED`, `NO_CANDIDATE` 등으로 장애와 데이터 부족 상황을 구분
- **Implementation Timeline**: Step 5에서 gRPC 계약과 fallback 분기를 먼저 고정하고, 이후 실제 gRPC 호출 경로를 보강한 뒤 동일 시나리오를 재검증
- **Target Filtering**: 성별, 지역, 관심사 기준으로 1차 후보군 필터링 수행

#### 6. Matching & Ranking Boundary (Step 6)
- **L1 Candidate Search**: Elasticsearch 서버 사이드 `sort + limit`으로 ACTIVE 광고 후보를 최대 200개까지 조회
- **L2 Target Matching**: `AdMatcher`로 성별, 지역, 관심사 매칭 책임 분리
- **Ranking Boundary**: `AdRanker`로 최종 선택 책임을 분리하고, 현재는 기본 `maxBid` 기준으로 선택
- **Fallback Expansion**: `CANDIDATE_TIMEOUT`, `CANDIDATE_ERROR` 추가로 후보 조회 실패 원인 구분
- **Serving Safety**: DMP 조회뿐 아니라 후보 조회에도 timeout을 두고, 검색 문서 변환은 `AdDocumentMapper`로 단일화

#### 7. Budget Control (Step 7)
- **Budget Guard**: 예산 부족 광고는 서빙 후보에서 제외하고 다음 후보를 선택
- **Redis Atomic Spend**: Redis Lua script로 예산 확인과 차감을 하나의 작업처럼 처리
- **Budget Fallback**: 모든 후보의 예산이 부족하면 `BUDGET_EXHAUSTED`로 응답
- **Won-based Accounting**: 광고 비용은 원 단위로 반올림해 Redis 예산 차감에 사용

#### 8. Performance Baseline (Step 8)
- **Load Test Scenario**: k6 기반으로 `fashion`, `local`, `home` 3개 지면 광고 서빙 부하 테스트 구성
- **Candidate Cache**: 짧은 TTL의 후보 캐시로 반복 Elasticsearch 후보 조회 비용 감소
- **Executor Reuse**: 요청마다 생성하던 executor를 Spring Bean으로 분리해 비동기 실행 오버헤드 감소
- **Bottleneck Isolation**: Docker k6 네트워크 오류와 애플리케이션 내부 timeout을 분리해 관측
- **Local Baseline**: 로컬 단일 인스턴스 기준 250 VU에서 광고 응답 성공률 99% 이상 확인

#### 9. Observability Baseline (Step 9)
- **Prometheus Metrics**: `/actuator/prometheus`로 광고 서빙 요청 수, 응답 성공률, latency, fallback reason 노출
- **Grafana Dashboard**: `Ad Serving Overview` 대시보드로 p95/p99, fallback reason, Redis/ES 상태 관측
- **Exporter Integration**: Redis / Elasticsearch exporter를 통해 주요 의존 저장소 상태 수집
- **Alert Rules**: p99 지연, 광고 응답률 저하, timeout 증가, target down 기준의 Prometheus alert rule 구성
- **Metric Persistence**: Prometheus 데이터를 Docker volume에 저장해 컨테이너 재시작 이후에도 관측 데이터 유지

#### 10. Traceability Baseline (Step 10)
- **Trace ID Propagation**: `X-Trace-Id`를 요청/응답 경로에 포함해 요청 단위 추적 경로를 고정
- **MDC Context Bridge**: 병렬 실행 구간에서도 동일 Trace ID를 유지하도록 MDC 컨텍스트 전달
- **Fallback Correlation**: fallback reason과 Trace ID를 함께 기록해 장애와 데이터 미스 원인 추적 강화
- **Regression Check**: 추적성 추가 이후 기존 서빙 시나리오(정상/timeout/fallback) 회귀 테스트 확인

#### 11. K8s Deployment Readiness (Step 11)
- **K8s Manifests**: Deployment, Service, ConfigMap, Secret 기반 실행 구성 추가
- **Health Probes**: readiness / liveness probe로 Pod 상태 점검 기준 구성
- **Self-healing Check**: Pod 강제 종료 이후 재기동되는 흐름 확인
- **Resource Boundary**: requests / limits를 명시해 HPA 판단을 위한 기본 리소스 기준 설정
- **Scale-out Readiness**: 로컬 K8s에서 HPA manifest와 Prometheus scrape annotation을 준비하고, 자동 확장 가능 조건을 정리

#### 12. Event Pipeline (Step 12)
- **Tracking URLs**: 광고 응답에 `requestId`, `impressionUrl`, `clickTrackingUrl`을 포함해 노출/클릭 수집 경로 추가
- **Idempotency Guard**: Redis `SETNX`와 `eventId` 기준으로 중복 이벤트를 앞단에서 차단
- **Async Event Ingestion**: Kafka topic(`ad-impressions`, `ad-clicks`)으로 이벤트 저장을 응답 경로에서 분리
- **Click Redirect**: 클릭 이벤트 수집 후 광고주 landing URL로 redirect 처리
- **Event Metrics**: impression / click / duplicate / failure rate를 Prometheus 지표로 노출

---

## Key Features

- **Parallel Serving**: 유저 프로필과 광고 후보를 병렬 조회하여 응답 지연을 줄이는 구조
- **Timeout & Fallback**: DMP 30ms 타임아웃 기반 fallback 서빙
- **Failure Classification**: fallback reason으로 장애와 데이터 미스 구분
- **Multi-stage Filtering**: 성별, 지역, 관심사 기반 단계별 후보 필터링
- **Matching Boundary**: 후보 조회, 타겟 매칭, 최종 선택 책임을 분리해 이후 랭킹 기준 확장 가능
- **Budget Control**: Redis 기반 예산 차감으로 예산 부족 광고 서빙 방지
- **Performance Baseline**: k6 부하 테스트와 병목 분리를 통해 로컬 단일 인스턴스 기준 성능 기준선 수립
- **Observability**: Prometheus/Grafana 기반으로 latency, fallback, Redis/ES 상태를 지속 관측
- **Traceability**: Trace ID와 MDC 기반으로 요청 단위 원인 추적 경로 확보
- **K8s Readiness**: K8s 위에서 실행, 상태 점검, self-healing, scale-out 준비 조건 확인
- **Event Pipeline**: 노출/클릭 이벤트를 Kafka로 분리해 사용자 응답 경로와 저장 경로를 분리

---

## Local Observability

```powershell
docker compose up -d prometheus grafana redis-exporter elasticsearch-exporter
```

- Prometheus: `http://localhost:9091`
- Grafana: `http://localhost:3000` (`admin` / `admin`)
- App Metrics: `http://localhost:8080/actuator/prometheus`
- Grafana Dashboard: `Ad Server > Ad Serving Overview`

Prometheus는 아래 target을 수집합니다.

```text
ad-server-engine: host.docker.internal:8080/actuator/prometheus
redis: redis-exporter:9121
elasticsearch: elasticsearch-exporter:9114
```

Alert rule은 Prometheus의 `Alerts` 화면에서 확인할 수 있습니다.

---

## 기술 블로그 시리즈 (Design Rationale)

- **Vol 1.** [#1. 서비스 분석 (Patterns)](https://velog.io/@hoonyl/1.-%EB%8B%B9%EA%B7%BC-%EB%AC%B4%EC%8B%A0%EC%82%AC-%EC%98%A4%EB%8A%98%EC%9D%98%EC%A7%91-%EA%B4%91%EA%B3%A0%EB%A5%BC-%EC%A7%81%EC%A0%91-%EB%B6%84%EC%84%9D%ED%95%98%EB%A9%B0-%EC%84%A4%EA%B3%84%EC%9D%98-%EA%B7%BC%EA%B1%B0%EB%A5%BC-%EC%B0%BE%EB%8B%A4)
- **Vol 2-1.** [#2-1. 데이터 모델링 (Data Modeling)](https://velog.io/@hoonyl/2-1.-%EA%B3%A0%EC%84%B1%EB%8A%A5-%EC%84%9C%EB%B9%94-%EC%9C%84%ED%95%9C-%EB%8D%B0%EC%9D%B4%ED%84%B0-%EB%AA%A8%EB%8D%B8%EB%A7%81)
- **Vol 2-2.** [#2-2. 실행 구조 (Serving Structure)](https://velog.io/@hoonyl/2-2.-%EC%84%9C%EB%B9%99-%EC%86%8D%EB%8F%84%EB%A5%BC-%EB%81%8C%EC%96%B4%EC%98%AC%EB%A6%AC%EB%8A%94-%EC%8B%A4%ED%96%89-%EA%B5%AC%EC%A1%B0)
- **Vol 3.** [#3. 최적화의 본질 (Optimization)](https://velog.io/@hoonyl/3.-%EA%B4%91%EA%B3%A0-%EC%97%94%EC%A7%84-%EC%B5%9C%EC%A0%81%ED%99%94%EC%9D%98-%EB%B3%B8%EC%A7%88)
- **Vol 4.** [#4. MySQL과 Elasticsearch를 안전하게 동기화하기](https://velog.io/@hoonyl/4.-MySQL%EA%B3%BC-Elasticsearch%EB%A5%BC-%EC%95%88%EC%A0%84%ED%95%98%EA%B2%8C-%EB%8F%99%EA%B8%B0%ED%99%94%ED%95%98%EA%B8%B0)
- **Vol 5.** [#5. 멈추지 않는 광고 서빙 흐름 만들기](https://velog.io/@hoonyl/5.-%EB%A9%88%EC%B6%94%EC%A7%80-%EC%95%8A%EB%8A%94-%EA%B4%91%EA%B3%A0-%EC%84%9C%EB%B9%99-%ED%9D%90%EB%A6%84-%EB%A7%8C%EB%93%A4%EA%B8%B0)
- **Vol 6.** [#6. 광고 선택 로직을 나눈 이유](https://velog.io/@hoonyl/6.-%EA%B4%91%EA%B3%A0-%EC%84%A0%ED%83%9D-%EB%A1%9C%EC%A7%81%EC%9D%84-%EB%82%98%EB%88%88-%EC%9D%B4%EC%9C%A0)
- **Vol 7.** [#7. 예산이 없는 광고를 막는 흐름 만들기](https://velog.io/@hoonyl/7.-%EC%98%88%EC%82%B0%EC%9D%B4-%EC%97%86%EB%8A%94-%EA%B4%91%EA%B3%A0%EB%A5%BC-%EB%A7%89%EB%8A%94-%ED%9D%90%EB%A6%84-%EB%A7%8C%EB%93%A4%EA%B8%B0)
- **Vol 8.** [#8. 부하 테스트에서 먼저 분리한 것](https://velog.io/@hoonyl/8.-%EB%B6%80%ED%95%98-%ED%85%8C%EC%8A%A4%ED%8A%B8%EC%97%90%EC%84%9C-%EB%A8%BC%EC%A0%80-%EB%B6%84%EB%A6%AC%ED%95%9C-%EA%B2%83)
- **Vol 9.** [#9. 부하 테스트 지표를 계속 볼 수 있게 만들기](https://velog.io/@hoonyl/9.-%EB%B6%80%ED%95%98-%ED%85%8C%EC%8A%A4%ED%8A%B8-%EC%A7%80%ED%91%9C%EB%A5%BC-%EA%B3%84%EC%86%8D-%EB%B3%BC-%EC%88%98-%EC%9E%88%EA%B2%8C-%EB%A7%8C%EB%93%A4%EA%B8%B0)
- **Vol 10.** [#10. 문제가 보였을 때 요청을 따라갈 수 있게 만들기](https://velog.io/@hoonyl/10.-%EB%AC%B8%EC%A0%9C%EA%B0%80-%EB%B3%B4%EC%98%80%EC%9D%84-%EB%95%8C-%EC%9A%94%EC%B2%AD%EC%9D%84-%EB%94%B0%EB%9D%BC%EA%B0%88-%EC%88%98-%EC%9E%88%EA%B2%8C-%EB%A7%8C%EB%93%A4%EA%B8%B0)
- **Vol 11.** [#11. 광고 서버를 K8s 위에서 실행해보기](https://velog.io/@hoonyl/11.-%EA%B4%91%EA%B3%A0-%EC%84%9C%EB%B2%84%EB%A5%BC-K8s-%EC%9C%84%EC%97%90%EC%84%9C-%EC%8B%A4%ED%96%89%ED%95%B4%EB%B3%B4%EA%B8%B0)
- **Vol 12.** [#12. 이벤트 수집 파이프라인을 분리한 이유](https://velog.io/@hoonyl/12.-%EC%9D%B4%EB%B2%A4%ED%8A%B8-%EC%88%98%EC%A7%91-%ED%8C%8C%EC%9D%B4%ED%94%84%EB%9D%BC%EC%9D%B8%EC%9D%84-%EB%B6%84%EB%A6%AC%ED%95%9C-%EC%9D%B4%EC%9C%A0)

---

## Tech Stack

- **Language/Framework**: Java 21, Spring Boot 3.4.0
- **Database**: MySQL 8.0, Redis
- **Search Engine**: Elasticsearch 8.15.0
- **Communication**: gRPC (Protobuf 3)
- **Tools**: Gradle, Docker Compose, Kubernetes
