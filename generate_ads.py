import json

template = "INSERT INTO ad (advertiser_id, title, image_url, click_url, max_bid, total_budget, spent_amount, start_date, status, target_gender, target_location_id, target_interest_tags, target_context, created_at, modified_at) VALUES ({}, '{}', '{}', '{}', {}, {}, 0, NOW(), 'ACTIVE', '{}', '{}', '{}', '{}', NOW(), NOW());"

ads = []

# Musinsa (1) - 40 ads
for i in range(1, 41):
    gender = 'M' if i % 2 == 0 else 'F'
    loc = '1:11' if i % 2 == 0 else '1:0'
    ads.append(template.format(1, f'무신사 패션 아이템 {i}', f'https://img.musinsa.com/{i}.jpg', f'https://musinsa.com/ad/{i}', 1000 + (i%10)*100, 1000000, gender, loc, '패션,의류', json.dumps({"age": 20 + (i%10)})))

# Karrot (2) - 40 ads
for i in range(1, 41):
    ads.append(template.format(2, f'당근마켓 지역광고 {i}', f'https://daangn.com/{i}.jpg', f'https://daangn.com/ad/{i}', 500 + (i%10)*50, 500000, 'ALL', '2:21', '로컬,중고', json.dumps({"category": "local"})))

# Ohouse (3) - 40 ads
for i in range(1, 41):
    ads.append(template.format(3, f'오늘의집 가구추천 {i}', f'https://ohou.se/{i}.jpg', f'https://ohou.se/ad/{i}', 1500 + (i%10)*150, 2000000, 'F', '0', '가구,인테리어', json.dumps({"brand": "ikea"})))

# Baemin (4) - 40 ads
for i in range(1, 41):
    ads.append(template.format(4, f'배달의민족 맛집 {i}', f'https://baemin.com/{i}.jpg', f'https://baemin.com/ad/{i}', 2000 + (i%10)*200, 5000000, 'ALL', '1:0', '음식,치킨', json.dumps({"first_order": True})))

with open('bulk_ads.sql', 'w', encoding='utf-8') as f:
    for ad in ads:
        f.write(ad + '\n')
