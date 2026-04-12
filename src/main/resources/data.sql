-- ====================================================================
-- 1. 광고주 초기 데이터 (Advertiser)
-- ====================================================================
INSERT INTO advertiser (id, name, status, created_at, modified_at) VALUES (1, '패션 브랜드', 'ACTIVE', NOW(), NOW());
INSERT INTO advertiser (id, name, status, created_at, modified_at) VALUES (2, '로컬 상점', 'ACTIVE', NOW(), NOW());
INSERT INTO advertiser (id, name, status, created_at, modified_at) VALUES (3, '홈 스타일러스', 'ACTIVE', NOW(), NOW());

-- ====================================================================
-- 2. 광고 초기 데이터 (Ad - Manual Diverse Targeting)
-- ====================================================================

-- [FASHION (ID 1)]
INSERT INTO ad (advertiser_id, title, image_url, click_url, max_bid, total_budget, spent_amount, start_date, status, target_gender, target_location_id, target_interest_tags, target_context, created_at, modified_at) VALUES (1, '패션 브랜드 스니커즈 특가', 'https://img.musinsa.com/1.jpg', 'https://musinsa.com/1', 1100, 1000000, 0, NOW(), 'ACTIVE', 'M', '1:11', '신발,패션', '{"age":21}', NOW(), NOW());
INSERT INTO ad (advertiser_id, title, image_url, click_url, max_bid, total_budget, spent_amount, start_date, status, target_gender, target_location_id, target_interest_tags, target_context, created_at, modified_at) VALUES (1, '패션 브랜드 여성 데일리룩', 'https://img.musinsa.com/2.jpg', 'https://musinsa.com/2', 1200, 1000000, 0, NOW(), 'ACTIVE', 'F', '1:12', '의류,데일리', '{"age":25}', NOW(), NOW());

-- [LOCAL (ID 2)]
INSERT INTO ad (advertiser_id, title, image_url, click_url, max_bid, total_budget, spent_amount, start_date, status, target_gender, target_location_id, target_interest_tags, target_context, created_at, modified_at) VALUES (2, '로컬 상점 강남지점 광고', 'https://daangn.com/1.png', 'https://daangn.com/1', 600, 500000, 0, NOW(), 'ACTIVE', 'ALL', '1:11', '로컬,강남', '{"category":"local"}', NOW(), NOW());
INSERT INTO ad (advertiser_id, title, image_url, click_url, max_bid, total_budget, spent_amount, start_date, status, target_gender, target_location_id, target_interest_tags, target_context, created_at, modified_at) VALUES (2, '로컬 상점 IT 구인 광고', 'https://daangn.com/5.png', 'https://daangn.com/5', 1200, 1000000, 0, NOW(), 'ACTIVE', 'ALL', '2:21', 'IT,개발', '{"category":"job"}', NOW(), NOW());

-- [HOME (ID 3)]
INSERT INTO ad (advertiser_id, title, image_url, click_url, max_bid, total_budget, spent_amount, start_date, status, target_gender, target_location_id, target_interest_tags, target_context, created_at, modified_at) VALUES (3, '홈 스타일러스 소파 기획전', 'https://ohou.se/1.png', 'https://ohou.se/1', 1500, 2000000, 0, NOW(), 'ACTIVE', 'F', '1:0', '가구,인테리어', '{"household":"single"}', NOW(), NOW());
INSERT INTO ad (advertiser_id, title, image_url, click_url, max_bid, total_budget, spent_amount, start_date, status, target_gender, target_location_id, target_interest_tags, target_context, created_at, modified_at) VALUES (3, '홈 스타일러스 침대 브랜드전', 'https://ohou.se/3.png', 'https://ohou.se/3', 3000, 5000000, 0, NOW(), 'ACTIVE', 'ALL', '0', '침대,브랜드', '{"household":"married"}', NOW(), NOW());

-- [테스트용 PAUSED/EXPIRED]
INSERT INTO ad (advertiser_id, title, image_url, click_url, max_bid, total_budget, spent_amount, start_date, status, target_gender, target_location_id, target_interest_tags, target_context, created_at, modified_at) VALUES (1, '종료된 시즌오프 광고', 'https://img.musinsa.com/expired.jpg', 'https://musinsa.com/expired', 100, 100000, 100000, NOW(), 'PAUSED', 'ALL', '0', '종료', '{}', NOW(), NOW());
