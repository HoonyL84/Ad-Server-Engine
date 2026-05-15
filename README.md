# Ad Server Engine

?몃옒??利앷? ?곹솴?먯꽌 愿묎퀬 ?묐떟 吏?곌낵 ?몃? ?섏〈??臾몄젣瑜?以꾩씠湲??꾪빐,  
?ㅼ떆媛?愿묎퀬 ?쒕튃 援ъ“瑜??ㅺ퀎?섍퀬 援ы쁽?섎뒗 ?꾨줈?앺듃?낅땲??

---

## ?렞 Why This Project?

蹂??꾨줈?앺듃??愿묎퀬 ?쒕튃 怨쇱젙?먯꽌 諛쒖깮?????덈뒗 吏?곌낵 ?μ븷 ?꾪뙆瑜?以꾩씠??怨쇱젙??湲곕줉?⑸땲??

- ?몃? ?섏〈??DMP) 吏?곗씠 ?꾩껜 愿묎퀬 ?묐떟 吏?곗쑝濡??댁뼱吏??臾몄젣瑜??닿껐?⑸땲??
- 媛쒖씤???뺥솗?꾨낫???묐떟 ?덉젙?깆쓣 ?곗꽑?섎뒗 ?쒕튃 ?꾨왂??寃利앺빀?덈떎.
- fallback 湲곕컲???덉젙?곸씤 愿묎퀬 ?쒕튃 援ъ“瑜??ㅺ퀎?섍퀬 援ы쁽?⑸땲??

---

## ?룛截?Engineering Milestones

### **Phase 1: 媛쒕컻 以鍮?諛??꾨찓??援ъ텞**

#### 1. Core Environment (Step 1)
- **Java 21 & Virtual Threads**: I/O 諛붿슫???묒뾽 理쒖쟻?붾? ?꾪븳 理쒖떊 ?고????섍꼍 援ъ텞
- **Infrastructure**: Docker Compose 湲곕컲??MySQL, Redis, Kafka ?듭떖 ?명봽???뗭뾽

#### 2. Interface Definition (Step 2)
- **DMP Integration**: gRPC(Protobuf 3)瑜??쒖슜???몃? ?좎? ?꾨줈???곕룞 洹쒓꺽 ?뺤쓽
- **?μ븷 寃⑸━**: ?몃? ?섏〈?깃낵??紐낇솗???꾨찓??寃쎄퀎 ?ㅼ젙

#### 3. Domain Readiness (Step 3)
- **2-Tier Modeling**: ?쒕튃 ?쒖젏??Join ?쒓굅瑜??꾪븳 Advertiser-Ad 2怨꾩링 援ъ“ ?뺣┰
- **Hybrid Targeting**: ?깅퀎, 吏??怨꾩링??ID), 愿?ъ궗蹂?怨좎냽 ?꾪꽣留곸쓣 ?꾪븳 ?ㅽ궎留??ㅺ퀎
- **Development Seeding**: 濡쒖뺄 寃利앹쓣 ?꾪븳 3,000嫄??댁긽??愿묎퀬 ?곗씠???곸옱 ?섍꼍 援ъ꽦
- **Mock DMP Infrastructure**: ?좎? ?꾨줈??議고쉶 ?쒕??덉씠?섏쓣 ?꾪븳 100,000紐낆쓽 ?좎? ?곗씠??援ъ꽦

#### 4. Search Synchronization (Step 4)
- **AdDocument Mapping**: Elasticsearch ?꾩슜 臾몄꽌 紐⑤뜽怨?寃??由ы룷吏?좊━ ?뺤쓽
- **Event-driven Indexing**: 愿묎퀬 ?앹꽦 ?몃옖??뀡 而ㅻ컠 ?댄썑 ES 鍮꾨룞湲??됱씤 泥섎━
- **Bulk Sync API**: 珥덇린 ?뺥빀???뺣낫瑜??꾪븳 MySQL?묮lasticsearch ?쇨큵 ?숆린??API ?쒓났

#### 5. Serving Orchestration (Step 5)
- **Parallel Lookup**: DMP ?좎? ?꾨줈??議고쉶? Elasticsearch 愿묎퀬 ?꾨낫 議고쉶瑜?Virtual Thread 湲곕컲?쇰줈 蹂묐젹 ?ㅽ뻾
- **DMP Timeout & Fallback**: ?좎? ?꾨줈??議고쉶??湲곕낯 30ms ??꾩븘?껋쓣 ?먭퀬, 珥덇낵/?ㅽ뙣 ??愿묎퀬 ?꾨낫 湲곕컲 fallback ?묐떟 ?쒓났
- **Fallback Reason 遺꾨━**: `PROFILE_NOT_FOUND`, `DMP_TIMEOUT`, `TARGET_NOT_MATCHED`, `NO_CANDIDATE` ?깆쑝濡??μ븷? ?곗씠??遺議??곹솴??援щ텇
- **Implementation Timeline**: Step 5?먯꽌 gRPC 怨꾩빟怨?fallback 遺꾧린瑜?癒쇱? 怨좎젙?섍퀬, ?댄썑 ?ㅼ젣 gRPC ?몄텧 寃쎈줈瑜?蹂닿컯?????숈씪 ?쒕굹由ъ삤瑜??ш?利?- **Target Filtering**: ?깅퀎, 吏?? 愿?ъ궗 湲곗??쇰줈 1李??꾨낫援??꾪꽣留??섑뻾

#### 6. Matching & Ranking Boundary (Step 6)
- **L1 Candidate Search**: Elasticsearch ?쒕쾭 ?ъ씠??`sort + limit`?쇰줈 ACTIVE 愿묎퀬 ?꾨낫瑜?理쒕? 200媛쒓퉴吏 議고쉶
- **L2 Target Matching**: `AdMatcher`濡??깅퀎, 吏?? 愿?ъ궗 留ㅼ묶 梨낆엫 遺꾨━
- **Ranking Boundary**: `AdRanker`濡?理쒖쥌 ?좏깮 梨낆엫??遺꾨━?섍퀬, ?꾩옱??湲곕낯 `maxBid` 湲곗??쇰줈 ?좏깮
- **Fallback Expansion**: `CANDIDATE_TIMEOUT`, `CANDIDATE_ERROR` 異붽?濡??꾨낫 議고쉶 ?ㅽ뙣 ?먯씤 援щ텇
- **Serving Safety**: DMP 議고쉶肉??꾨땲???꾨낫 議고쉶?먮룄 timeout???먭퀬, 寃??臾몄꽌 蹂?섏? `AdDocumentMapper`濡??⑥씪??
#### 7. Budget Control (Step 7)
- **Budget Guard**: ?덉궛 遺議?愿묎퀬???쒕튃 ?꾨낫?먯꽌 ?쒖쇅?섍퀬 ?ㅼ쓬 ?꾨낫瑜??좏깮
- **Redis Atomic Spend**: Redis Lua script濡??덉궛 ?뺤씤怨?李④컧???섎굹???묒뾽泥섎읆 泥섎━
- **Budget Fallback**: 紐⑤뱺 ?꾨낫???덉궛??遺議깊븯硫?`BUDGET_EXHAUSTED`濡??묐떟
- **Won-based Accounting**: 愿묎퀬 鍮꾩슜? ???⑥쐞濡?諛섏삱由쇳빐 Redis ?덉궛 李④컧???ъ슜

#### 8. Performance Baseline (Step 8)
- **Load Test Scenario**: k6 湲곕컲?쇰줈 `fashion`, `local`, `home` 3媛?吏硫?愿묎퀬 ?쒕튃 遺???뚯뒪??援ъ꽦
- **Candidate Cache**: 吏㏃? TTL???꾨낫 罹먯떆濡?諛섎났 Elasticsearch ?꾨낫 議고쉶 鍮꾩슜 媛먯냼
- **Executor Reuse**: ?붿껌留덈떎 ?앹꽦?섎뜕 executor瑜?Spring Bean?쇰줈 遺꾨━??鍮꾨룞湲??ㅽ뻾 ?ㅻ쾭?ㅻ뱶 媛먯냼
- **Bottleneck Isolation**: Docker k6 ?ㅽ듃?뚰겕 ?ㅻ쪟? ?좏뵆由ъ??댁뀡 ?대? timeout??遺꾨━??愿痢?- **Local Baseline**: 濡쒖뺄 ?⑥씪 ?몄뒪?댁뒪 湲곗? 250 VU?먯꽌 愿묎퀬 ?묐떟 ?깃났瑜?99% ?댁긽 ?뺤씤

#### 9. Observability Baseline (Step 9)
- **Prometheus Metrics**: `/actuator/prometheus`濡?愿묎퀬 ?쒕튃 ?붿껌 ?? ?묐떟 ?깃났瑜? latency, fallback reason ?몄텧
- **Grafana Dashboard**: `Ad Serving Overview` ??쒕낫?쒕줈 p95/p99, fallback reason, Redis/ES ?곹깭 愿痢?- **Exporter Integration**: Redis / Elasticsearch exporter瑜??듯빐 二쇱슂 ?섏〈 ??μ냼 ?곹깭 ?섏쭛
- **Alert Rules**: p99 吏?? 愿묎퀬 ?묐떟瑜???? timeout 利앷?, target down 湲곗???Prometheus alert rule 援ъ꽦
- **Metric Persistence**: Prometheus ?곗씠?곕? Docker volume????ν빐 而⑦뀒?대꼫 ?ъ떆???댄썑?먮룄 愿痢??곗씠???좎?

#### 10. Traceability Baseline (Step 10)
- **Trace ID Propagation**: X-Trace-Id를 요청/응답 경로에 포함해 요청 단위 추적 경로를 고정
- **MDC Context Bridge**: 병렬 실행 구간에서도 동일 Trace ID를 유지하도록 MDC 컨텍스트 전달
- **Fallback Correlation**: fallback reason과 Trace ID를 함께 기록해 장애/데이터 미스 원인 추적 강화
- **Regression Check**: 추적성 추가 이후 기존 서빙 시나리오(정상/timeout/fallback) 회귀 테스트 확인
---

## ?? Key Features

- **Parallel Serving**: ?좎? ?꾨줈?꾧낵 愿묎퀬 ?꾨낫瑜?蹂묐젹 議고쉶?섏뿬 ?묐떟 吏?곗쓣 以꾩씠??援ъ“
- **Timeout & Fallback**: DMP 30ms ??꾩븘??湲곕컲 fallback ?쒕튃
- **Failure Classification**: fallback reason?쇰줈 ?μ븷? ?곗씠??誘몄뒪 援щ텇
- **Multi-stage Filtering**: ?깅퀎, 吏?? 愿?ъ궗 湲곕컲 ?④퀎蹂??꾨낫 ?꾪꽣留?- **Matching Boundary**: ?꾨낫 議고쉶, ?寃?留ㅼ묶, 理쒖쥌 ?좏깮 梨낆엫??遺꾨━???댄썑 ??궧 湲곗? ?뺤옣 媛??- **Budget Control**: Redis 湲곕컲 ?덉궛 李④컧?쇰줈 ?덉궛 遺議?愿묎퀬 ?쒕튃 諛⑹?
- **Performance Baseline**: k6 遺???뚯뒪?몄? 蹂묐ぉ 遺꾨━瑜??듯빐 濡쒖뺄 ?⑥씪 ?몄뒪?댁뒪 湲곗? ?깅뒫 湲곗????섎┰
- **Observability**: Prometheus/Grafana 湲곕컲?쇰줈 latency, fallback, Redis/ES ?곹깭瑜?吏??愿痢?
---

## ?뱤 Local Observability

```powershell
docker compose up -d prometheus grafana redis-exporter elasticsearch-exporter
```

- Prometheus: `http://localhost:9091`
- Grafana: `http://localhost:3000` (`admin` / `admin`)
- App Metrics: `http://localhost:8080/actuator/prometheus`
- Grafana Dashboard: `Ad Server > Ad Serving Overview`

Prometheus???꾨옒 target???섏쭛?⑸땲??

```text
ad-server-engine: host.docker.internal:8080/actuator/prometheus
redis: redis-exporter:9121
elasticsearch: elasticsearch-exporter:9114
```

Alert rule? Prometheus??`Alerts` ?붾㈃?먯꽌 ?뺤씤?????덉뒿?덈떎.

---

## ?뱛 湲곗닠 釉붾줈洹??쒕━利?(Design Rationale)

- **Vol 1.** [#1. ?쒕퉬??遺꾩꽍 (Patterns)](https://velog.io/@hoonyl/1.-%EB%8B%B9%EA%B7%BC-%EB%AC%B4%EC%8B%A0%EC%82%AC-%EC%98%A4%EB%8A%98%EC%9D%98%EC%A7%91-%EA%B4%91%EA%B3%A0%EB%A5%BC-%EC%A7%81%EC%A0%91-%EB%B6%84%EC%84%9D%ED%95%98%EB%A9%B0-%EC%84%A4%EA%B3%84%EC%9D%98-%EA%B7%BC%EA%B1%B0%EB%A5%BC-%EC%B0%BE%EB%8B%A4)
- **Vol 2-1.** [#2-1. ?곗씠??紐⑤뜽留?(Data Modeling)](https://velog.io/@hoonyl/2-1.-%EA%B3%A0%EC%84%B1%EB%8A%A5-%EC%84%9C%EB%B9%94-%EC%9C%84%ED%95%9C-%EB%8D%B0%EC%9D%B4%ED%84%B0-%EB%AA%A8%EB%8D%B8%EB%A7%81)
- **Vol 2-2.** [#2-2. ?ㅽ뻾 援ъ“ (Serving Structure)](https://velog.io/@hoonyl/2-2.-%EC%84%9C%EB%B9%99-%EC%86%8D%EB%8F%84%EB%A5%BC-%EB%81%8C%EC%96%B4%EC%98%AC%EB%A6%AC%EB%8A%94-%EC%8B%A4%ED%96%89-%EA%B5%AC%EC%A1%B0)
- **Vol 3.** [#3. 理쒖쟻?붿쓽 蹂몄쭏 (Optimization)](https://velog.io/@hoonyl/3.-%EA%B4%91%EA%B3%A0-%EC%97%94%EC%A7%84-%EC%B5%9C%EC%A0%81%ED%99%94%EC%9D%98-%EB%B3%B8%EC%A7%88)
- **Vol 4.** [#4. MySQL怨?Elasticsearch瑜??덉쟾?섍쾶 ?숆린?뷀븯湲?(https://velog.io/@hoonyl/4.-MySQL%EA%B3%BC-Elasticsearch%EB%A5%BC-%EC%95%88%EC%A0%84%ED%95%98%EA%B2%8C-%EB%8F%99%EA%B8%B0%ED%99%94%ED%95%98%EA%B8%B0)
- **Vol 5.** [#5. 硫덉텛吏 ?딅뒗 愿묎퀬 ?쒕튃 ?먮쫫 留뚮뱾湲?(https://velog.io/@hoonyl/5.-%EB%A9%88%EC%B6%94%EC%A7%80-%EC%95%8A%EB%8A%94-%EA%B4%91%EA%B3%A0-%EC%84%9C%EB%B9%99-%ED%9D%90%EB%A6%84-%EB%A7%8C%EB%93%A4%EA%B8%B0)
- **Vol 6.** [#6. 愿묎퀬 ?좏깮 濡쒖쭅???섎늿 ?댁쑀](https://velog.io/@hoonyl/6.-%EA%B4%91%EA%B3%A0-%EC%84%A0%ED%83%9D-%EB%A1%9C%EC%A7%81%EC%9D%84-%EB%82%98%EB%88%88-%EC%9D%B4%EC%9C%A0)
- **Vol 7.** [#7. ?덉궛???녿뒗 愿묎퀬瑜?留됰뒗 ?먮쫫 留뚮뱾湲?(https://velog.io/@hoonyl/7.-%EC%98%88%EC%82%B0%EC%9D%B4-%EC%97%86%EB%8A%94-%EA%B4%91%EA%B3%A0%EB%A5%BC-%EB%A7%89%EB%8A%94-%ED%9D%90%EB%A6%84-%EB%A7%8C%EB%93%A4%EA%B8%B0)
- **Vol 8.** [#8. 遺???뚯뒪?몄뿉??癒쇱? 遺꾨━??寃?(https://velog.io/@hoonyl/8.-%EB%B6%80%ED%95%98-%ED%85%8C%EC%8A%A4%ED%8A%B8%EC%97%90%EC%84%9C-%EB%A8%BC%EC%A0%80-%EB%B6%84%EB%A6%AC%ED%95%9C-%EA%B2%83)
- **Vol 9.** [#9. 遺???뚯뒪??吏?쒕? 怨꾩냽 蹂????덇쾶 留뚮뱾湲?(https://velog.io/@hoonyl/9.-%EB%B6%80%ED%95%98-%ED%85%8C%EC%8A%A4%ED%8A%B8-%EC%A7%80%ED%91%9C%EB%A5%BC-%EA%B3%84%EC%86%8D-%EB%B3%BC-%EC%88%98-%EC%9E%88%EA%B2%8C-%EB%A7%8C%EB%93%A4%EA%B8%B0)
- **Vol 10.** [#10. 문제가 보였을 때 요청을 따라갈 수 있게 만들기](https://velog.io/@hoonyl/10.-%EB%AC%B8%EC%A0%9C%EA%B0%80-%EB%B3%B4%EC%98%80%EC%9D%84-%EB%95%8C-%EC%9A%94%EC%B2%AD%EC%9D%84-%EB%94%B0%EB%9D%BC%EA%B0%88-%EC%88%98-%EC%9E%88%EA%B2%8C-%EB%A7%8C%EB%93%A4%EA%B8%B0)

---

## ?썱 Tech Stack

- **Language/Framework**: Java 21, Spring Boot 3.4.0
- **Database**: MySQL 8.0, Redis
- **Search Engine**: Elasticsearch 8.15.0
- **Communication**: gRPC (Protobuf 3)
- **Tools**: Gradle, Docker Compose

