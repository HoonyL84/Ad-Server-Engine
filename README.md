# Ad Server Engine

트래픽 증가 상황에서 광고 응답 지연과 외부 의존성 문제를 줄이기 위해,  
실시간 광고 서빙 구조를 설계하고 구현하는 프로젝트입니다.

---

## 🎯 Why This Project?

본 프로젝트는 광고 서빙 과정에서 발생할 수 있는 지연과 장애 전파를 줄이는 과정을 기록합니다.

- 외부 의존성(DMP) 지연이 전체 광고 응답 지연으로 이어지는 문제를 해결합니다.
- 개인화 정확도보다 응답 안정성을 우선하는 서빙 전략을 검증합니다.
- fallback 기반의 안정적인 광고 서빙 구조를 설계하고 구현합니다.

---

## 🏗️ Engineering Milestones

### **Phase 1: 개발 준비 및 도메인 구축**

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
- **Bulk Sync API**: 초기 정합성 확보를 위한 MySQL→Elasticsearch 일괄 동기화 API 제공

#### 5. Serving Orchestration (Step 5)
- **Parallel Lookup**: DMP 유저 프로필 조회와 Elasticsearch 광고 후보 조회를 Virtual Thread 기반으로 병렬 실행
- **DMP Timeout & Fallback**: 유저 프로필 조회에 기본 30ms 타임아웃을 두고, 초과/실패 시 광고 후보 기반 fallback 응답 제공
- **Fallback Reason 분리**: `PROFILE_NOT_FOUND`, `DMP_TIMEOUT`, `TARGET_NOT_MATCHED`, `NO_CANDIDATE` 등으로 장애와 데이터 부족 상황을 구분
- **Target Filtering**: 성별, 지역, 관심사 기준으로 1차 후보군 필터링 수행

---

## 🚀 Key Features

- **Parallel Serving**: 유저 프로필과 광고 후보를 병렬 조회하여 응답 지연 최소화
- **Timeout & Fallback**: DMP 30ms 타임아웃 기반 fallback 서빙
- **Failure Classification**: fallback reason으로 장애와 데이터 미스 구분
- **Multi-stage Filtering**: 성별, 지역, 관심사 기반 단계별 후보 필터링

---

## 📂 기술 블로그 시리즈 (Design Rationale)

- **Vol 1.** [#1. 서비스 분석 (Patterns)](https://velog.io/@hoonyl/1.-%EB%8B%B9%EA%B7%BC-%EB%AC%B4%EC%8B%A0%EC%82%AC-%EC%98%A4%EB%8A%98%EC%9D%98%EC%A7%91-%EA%B4%91%EA%B3%A0%EB%A5%BC-%EC%A7%81%EC%A0%91-%EB%B6%84%EC%84%9D%ED%95%98%EB%A9%B0-%EC%84%A4%EA%B3%84%EC%9D%98-%EA%B7%BC%EA%B1%B0%EB%A5%BC-%EC%B0%BE%EB%8B%A4)
- **Vol 2-1.** [#2-1. 데이터 모델링 (Data Modeling)](https://velog.io/@hoonyl/2-1.-%EA%B3%A0%EC%84%B1%EB%8A%A5-%EC%84%9C%EB%B9%94-%EC%9C%84%ED%95%9C-%EB%8D%B0%EC%9D%B4%ED%84%B0-%EB%AA%A8%EB%8D%B8%EB%A7%81)
- **Vol 2-2.** [#2-2. 실행 구조 (Serving Structure)](https://velog.io/@hoonyl/2-2.-%EC%84%9C%EB%B9%99-%EC%86%8D%EB%8F%84%EB%A5%BC-%EB%81%8C%EC%96%B4%EC%98%AC%EB%A6%AC%EB%8A%94-%EC%8B%A4%ED%96%89-%EA%B5%AC%EC%A1%B0)
- **Vol 3.** [#3. 최적화의 본질 (Optimization)](https://velog.io/@hoonyl/3.-%EA%B4%91%EA%B3%A0-%EC%97%94%EC%A7%84-%EC%B5%9C%EC%A0%81%ED%99%94%EC%9D%98-%EB%B3%B8%EC%A7%88)
- **Vol 4.** [#4. MySQL과 Elasticsearch를 안전하게 동기화하기](https://velog.io/@hoonyl/4.-MySQL%EA%B3%BC-Elasticsearch%EB%A5%BC-%EC%95%88%EC%A0%84%ED%95%98%EA%B2%8C-%EB%8F%99%EA%B8%B0%ED%99%94%ED%95%98%EA%B8%B0)
- **Vol 5.** [#5. 멈추지 않는 광고 서빙 흐름 만들기](https://velog.io/@hoonyl/5.-%EB%A9%88%EC%B6%94%EC%A7%80-%EC%95%8A%EB%8A%94-%EA%B4%91%EA%B3%A0-%EC%84%9C%EB%B9%99-%ED%9D%90%EB%A6%84-%EB%A7%8C%EB%93%A4%EA%B8%B0)

---

## 🛠 Tech Stack

- **Language/Framework**: Java 21, Spring Boot 3.4.0
- **Database**: MySQL 8.0, Redis
- **Search Engine**: Elasticsearch 8.15.0
- **Communication**: gRPC (Protobuf 3)
- **Tools**: Gradle, Docker Compose
