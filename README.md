# Ad Server Engine: High-Performance Speculative Serving

17년간 광고 플랫폼을 운영하며 축적된 도메인 노하우를 바탕으로, **"대규모 트래픽 하에서의 초저지연 실시간 타게팅"** 아키텍처를 현대적으로 구현하고 그 기술적 완성도를 실전적으로 증명하기 위한 프로젝트입니다. 

---

## 🎯 Why This Project?

본 프로젝트는 실무에서 직면하는 고난도 기술적 과제들을 최신 아키텍처로 재해석하고 최적화하는 과정을 기록합니다.

- **설계의 근거 (The Rationale)**: 모든 구현 단계에서 "왜 이 기술인가?"에 대한 설계 근거와 사고 과정을 기록으로 남깁니다.
- **실전 패턴의 이식**: 당근, 무신사, 오늘의집 등 국내 주요 서빙 플랫폼의 아키텍처 패턴을 분석하여 실전 수준의 구조를 설계했습니다.
- **High-Performance Scalability**: Java 21 Virtual Threads, gRPC, Elasticsearch 등 최신 스택을 활용하여 극한의 성능을 추구합니다.

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
- **Development Seeding**: 실전 엔진 개발을 위한 3,000건 이상의 고밀도 광고 데이터 적재 환경 완비
- **Mock DMP Infrastructure**: gRPC 기반 유저 프로필 조회 시뮬레이션을 위한 100,000명의 유저 페르소나 구축 (Redis Pipelining 최적화)

---

## 🚀 Key Features (Design Preview)

분석된 서비스들의 실전적 전략을 반영한 **Multi-Stage Pipeline** 구조를 채택하였습니다.

- **Hard-Filter Layer**: (무신사 사례 반영) 성별, 연령 등 유저 프로필 기반의 절대적 제약 조건을 시스템 최상단에서 검증하여 도메인 무결성 보호
- **Path-based Location Matcher**: (당근 사례 반영) 계층형 지역 ID(Neighborhood ID) 체계를 활용하여, 지역 기반의 고속 매칭 수행
- **Interest-based Matcher**: (오늘의집 사례 반영) 유저의 관심사 및 과거 구매 내역을 바탕으로 최적의 광고 소재를 매칭하는 로직 구현

---

## 📂 기술 블로그 시리즈 (Design Rationale)

- **Vol 1.** [#1. 서비스 분석 (Patterns)](https://velog.io/@hoonyl/1.-%EB%8B%B9%EA%B7%BC-%EB%AC%B4%EC%8B%A0%EC%82%AC-%EC%98%A4%EB%8A%98%EC%9D%98%EC%A7%91-%EA%B4%91%EA%B3%A0%EB%A5%BC-%EC%A7%81%EC%A0%91-%EB%B6%84%EC%84%9D%ED%95%98%EB%A9%B0-%EC%84%A4%EA%B3%84%EC%9D%98-%EA%B7%BC%EA%B1%B0%EB%A5%BC-%EC%B0%BE%EB%8B%A4)
- **Vol 2-1.** [#2-1. 데이터 모델링 (Data Modeling)](https://velog.io/@hoonyl/2-1.-%EA%B3%A0%EC%84%B1%EB%8A%A5-%EC%84%9C%EB%B9%94-%EC%9C%84%ED%95%9C-%EB%8D%B0%EC%9D%B4%ED%84%B0-%EB%AA%A8%EB%8D%B8%EB%A7%81)
- **Vol 2-2.** [#2-2. 실행 구조 (Serving Structure)](https://velog.io/@hoonyl/2-2.-%EC%84%9C%EB%B9%99-%EC%86%8D%EB%8F%84%EB%A5%BC-%EB%81%8C%EC%96%B4%EC%98%AC%EB%A6%AC%EB%8A%94-%EC%8B%A4%ED%96%89-%EA%B5%AC%EC%A1%B0)
- **Vol 3.** [#3. 최적화의 본질 (Optimization)](https://velog.io/@hoonyl/3.-%EA%B4%91%EA%B3%A0-%EC%97%94%EC%A7%84-%EC%B5%9C%EC%A0%81%ED%99%94%EC%9D%98-%EB%B3%B8%EC%A7%88)

---

## 🛠 Tech Stack

- **Lanuage/Framework**: Java 21 (Virtual Threads), Spring Boot 3.4.0
- **Database**: MySQL 8.0, Redis (Pipelining, Lua Script)
- **Search Engine**: Elasticsearch 8.15.0
- **Communication**: gRPC (Protobuf 3)
- **Tools**: Gradle, Docker Compose, QueryDSL, Testcontainers