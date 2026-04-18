-- ============================================================
-- Payper v2 부하테스트용 시드 데이터
-- 게시글 50,000건 + 사용자 500명 + 가맹점 100개 + 카테고리 40개
-- ============================================================
-- 실행 전: 기존 테스트 데이터 충돌 방지를 위해 확인 필요
-- 대상 DB: payper_v2 (MySQL 8.0+)
-- 예상 소요: 약 1~3분
-- ============================================================

SET SESSION cte_max_recursion_depth = 60000;
SET @start_time = NOW();

-- ============================================================
-- 1. 카테고리 (대분류 10개 + 소분류 30개 = 총 40개)
-- ============================================================
INSERT INTO category (name, parent_category_id) VALUES
-- 대분류
('음식점', NULL),       -- 1
('카페', NULL),         -- 2
('편의점', NULL),       -- 3
('마트', NULL),         -- 4
('주유소', NULL),       -- 5
('약국', NULL),         -- 6
('병원', NULL),         -- 7
('미용', NULL),         -- 8
('교통', NULL),         -- 9
('문화', NULL);         -- 10

SET @cat_base = LAST_INSERT_ID(); -- 첫 대분류 ID

INSERT INTO category (name, parent_category_id) VALUES
-- 음식점 하위
('한식', @cat_base), ('중식', @cat_base), ('일식', @cat_base),
-- 카페 하위
('프랜차이즈 카페', @cat_base+1), ('개인 카페', @cat_base+1), ('디저트', @cat_base+1),
-- 편의점 하위
('CU', @cat_base+2), ('GS25', @cat_base+2), ('세븐일레븐', @cat_base+2),
-- 마트 하위
('대형마트', @cat_base+3), ('슈퍼마켓', @cat_base+3),
-- 주유소 하위
('SK에너지', @cat_base+4), ('GS칼텍스', @cat_base+4), ('현대오일뱅크', @cat_base+4),
-- 약국 하위
('체인약국', @cat_base+5), ('동네약국', @cat_base+5),
-- 병원 하위
('내과', @cat_base+6), ('피부과', @cat_base+6), ('치과', @cat_base+6),
-- 미용 하위
('헤어', @cat_base+7), ('네일', @cat_base+7), ('피부관리', @cat_base+7),
-- 교통 하위
('택시', @cat_base+8), ('대리운전', @cat_base+8), ('주차장', @cat_base+8),
-- 문화 하위
('영화관', @cat_base+9), ('서점', @cat_base+9), ('공연', @cat_base+9),
-- 추가
('베이커리', @cat_base+1), ('패스트푸드', @cat_base);

-- ============================================================
-- 2. 가맹점 100개 (소분류 카테고리에 분산 배치)
-- ============================================================
INSERT INTO merchant (name, category_id, image_url)
WITH RECURSIVE seq AS (
    SELECT 1 AS n UNION ALL SELECT n+1 FROM seq WHERE n < 100
),
-- 소분류 카테고리 ID 목록 (대분류 10개 이후 = @cat_base+10 ~ @cat_base+39)
sub_cats AS (
    SELECT id FROM category WHERE parent_category_id IS NOT NULL ORDER BY id
)
SELECT
    CONCAT(
        ELT(
            (seq.n - 1) DIV 10 + 1,
            CONCAT('맛있는 ', ELT(((seq.n-1) % 10)+1, '김치찌개','된장찌개','비빔밥','냉면','삼겹살','짜장면','초밥','돈카츠','떡볶이','치킨')),
            ELT(((seq.n-1) % 10)+1, '스타벅스','투썸플레이스','이디야','메가커피','컴포즈','빽다방','할리스','폴바셋','블루보틀','카페베네'),
            ELT(((seq.n-1) % 10)+1, 'CU','GS25','세븐일레븐','이마트24','미니스톱','CU','GS25','세븐일레븐','CU','GS25'),
            ELT(((seq.n-1) % 10)+1, '이마트','홈플러스','코스트코','롯데마트','하나로마트','이마트','홈플러스','코스트코','롯데마트','트레이더스'),
            ELT(((seq.n-1) % 10)+1, 'SK주유소','GS칼텍스','현대오일뱅크','S-OIL','SK주유소','GS칼텍스','현대오일뱅크','S-OIL','SK주유소','GS칼텍스'),
            ELT(((seq.n-1) % 10)+1, '온누리약국','건강약국','행복약국','사랑약국','미소약국','온누리약국','건강약국','행복약국','사랑약국','미소약국'),
            ELT(((seq.n-1) % 10)+1, '서울내과','연세피부과','강남치과','미래내과','하나피부과','서울내과','연세치과','강남내과','미래피부과','하나치과'),
            ELT(((seq.n-1) % 10)+1, '준오헤어','이철헤어','박승철','리안','아이디','네일아트','뷰티랩','피부관리실','헤어샵','뷰티샵'),
            ELT(((seq.n-1) % 10)+1, '카카오T','타다','쏘카','파킹클라우드','모두의주차장','카카오T','타다','쏘카','그린파킹','아이파킹'),
            ELT(((seq.n-1) % 10)+1, 'CGV','메가박스','롯데시네마','교보문고','YES24','인터파크','알라딘','영풍문고','아트센터','공연장')
        ),
        ' ',
        LPAD(seq.n, 3, '0')
    ) AS name,
    -- 소분류 카테고리에 순환 배정
    @cat_base + 10 + ((seq.n - 1) % 30) AS category_id,
    CONCAT('https://cdn.payper.com/merchants/', seq.n, '.png') AS image_url
FROM seq;

-- ============================================================
-- 3. 사용자 500명
-- ============================================================
-- 활동적 사용자 비율: 90% active, 10% 비활성
-- 역할: 99% USER, 1% ADMIN
INSERT INTO users (user_identifier, auth_type, name, oauth_id, user_role, active)
WITH RECURSIVE seq AS (
    SELECT 1 AS n UNION ALL SELECT n+1 FROM seq WHERE n < 500
)
SELECT
    UUID() AS user_identifier,
    'KAKAO' AS auth_type,
    CONCAT(
        ELT(FLOOR(RAND()*20)+1, '김','이','박','최','정','강','조','윤','장','임','한','오','서','신','권','황','안','송','전','홍'),
        ELT(FLOOR(RAND()*30)+1, '민준','서준','도윤','예준','시우','하준','주원','지호','지후','준서','현우','도현','건우','우진','선우','서연','서윤','지우','서현','민서','하은','하윤','윤서','지민','지유','채원','수아','지아','다은','은서')
    ) AS name,
    CONCAT('kakao_', LPAD(seq.n, 6, '0')) AS oauth_id,
    IF(seq.n <= 5, 'ADMIN', 'USER') AS user_role,
    IF(RAND() < 0.9, TRUE, FALSE) AS active
FROM seq;

SET @user_base = LAST_INSERT_ID(); -- 첫 유저 ID
SET @merchant_base = (SELECT MIN(id) FROM merchant);

-- ============================================================
-- 4. 게시글 50,000건
-- ============================================================
-- 현실적인 분포:
--   게시글 타입: BENEFIT 45%, QUESTION 30%, ETC 25%
--   작성자 분포: 상위 10% 사용자가 전체 게시글의 50% 작성 (파레토)
--   가맹점 분포: 인기 가맹점 20개에 게시글 60% 집중
--   시간 분포:  최근 6개월, 최근일수록 게시글 밀도 높음
--   삭제 비율:  ~5% soft deleted
--   비활성 비율: ~1%
--   조회수:     로그 정규 분포 (대부분 적고, 일부 바이럴)
--   좋아요:     조회수의 3~15%
--   댓글 수:    조회수의 1~8%
--   신고 수:    대부분 0, 극히 일부 1~5
-- ============================================================

INSERT INTO posts (
    author_id, merchant_id, type, title, content,
    comment_count, view_count, like_count, report_count,
    is_inactive, inactive_at, is_deleted, deleted_at,
    created_at, updated_at
)
WITH RECURSIVE seq AS (
    SELECT 1 AS n UNION ALL SELECT n+1 FROM seq WHERE n < 50000
),
-- 인기 가맹점 20개 (전체 게시글의 60% 차지)
params AS (
    SELECT
        seq.n,
        RAND() AS r_type,
        RAND() AS r_author,
        RAND() AS r_merchant,
        RAND() AS r_delete,
        RAND() AS r_inactive,
        RAND() AS r_views_1,
        RAND() AS r_views_2,
        RAND() AS r_like_ratio,
        RAND() AS r_comment_ratio,
        RAND() AS r_report,
        RAND() AS r_title,
        RAND() AS r_content,
        RAND() AS r_time_weight,
        RAND() AS r_hour,
        RAND() AS r_minute
    FROM seq
)
SELECT
    -- ── 작성자: 파레토 분포 (상위 50명이 50% 작성) ──
    @user_base + CASE
        WHEN r_author < 0.50 THEN FLOOR(POWER(r_author / 0.50, 2) * 50)          -- 상위 50명 (전체의 10%)
        WHEN r_author < 0.80 THEN 50 + FLOOR((r_author - 0.50) / 0.30 * 150)     -- 51~200
        ELSE 200 + FLOOR((r_author - 0.80) / 0.20 * 300)                          -- 201~500
    END AS author_id,

    -- ── 가맹점: 인기 가맹점 집중 분포 ──
    @merchant_base + CASE
        WHEN r_merchant < 0.60 THEN FLOOR(r_merchant / 0.60 * 20)   -- 상위 20개 가맹점에 60%
        ELSE 20 + FLOOR((r_merchant - 0.60) / 0.40 * 80)            -- 나머지 80개 가맹점에 40%
    END AS merchant_id,

    -- ── 게시글 타입 ──
    CASE
        WHEN r_type < 0.45 THEN 'BENEFIT'
        WHEN r_type < 0.75 THEN 'QUESTION'
        ELSE 'ETC'
    END AS type,

    -- ── 제목: 타입별 현실적인 제목 템플릿 ──
    CASE
        WHEN r_type < 0.45 THEN -- BENEFIT
            ELT(FLOOR(r_title * 20) + 1,
                '이번 달 카드 혜택 정리합니다',
                '여기 포인트 적립률 진짜 좋네요',
                '할인 받는 꿀팁 공유',
                '이번 주 특가 정보 알려드립니다',
                '멤버십 혜택 업데이트 됐어요',
                '앱 결제하면 추가 할인 돼요',
                '제휴카드 할인 후기',
                '오늘 방문했는데 혜택이 이렇게나',
                '쿠폰 사용 후기 남깁니다',
                '적립금으로 공짜 이용한 썰',
                '이 카드 쓰면 여기서 10% 할인',
                '신규 회원 혜택 꼭 챙기세요',
                '주말 더블 적립 이벤트 중',
                '생일 쿠폰 사용 후기',
                '스탬프 다 모으면 이거 줘요',
                '연회비 뽕 뽑는 곳 여기',
                '체크카드로도 할인 되네요',
                '오프라인 전용 할인 정보',
                '여기 첫 방문 쿠폰 꿀이에요',
                '실적 채우기 좋은 곳'
            )
        WHEN r_type < 0.75 THEN -- QUESTION
            ELT(FLOOR(r_title * 20) + 1,
                '혜택 적용이 안 되는데 저만 그런가요?',
                '결제 오류 해결 방법 아시는 분?',
                '포인트 적립 누락됐는데 어디에 문의하나요',
                '환불 처리 얼마나 걸리나요?',
                '카드 할인이랑 쿠폰 중복 되나요?',
                '영수증에 할인 적용이 안 떠요',
                '멤버십 등급은 어떻게 올리나요?',
                '앱 쿠폰 사용하는 방법 좀 알려주세요',
                '여기 주차 할인 되나요?',
                '포인트 유효기간이 어떻게 되나요?',
                '법인카드로도 혜택 받을 수 있나요?',
                '적립금 사용 조건이 뭔가요?',
                '온라인이랑 오프라인 가격 다른가요?',
                '사전 예약하면 할인 있나요?',
                '가족카드로 결제해도 적립 되나요?',
                '현금영수증하면 포인트도 쌓이나요?',
                '교환/반품 절차가 어떻게 되나요?',
                '다른 분들은 보통 어떤 카드 쓰세요?',
                '이 근처에 비슷한 곳 추천 좀요',
                '예약 취소 수수료 있나요?'
            )
        ELSE -- ETC
            ELT(FLOOR(r_title * 20) + 1,
                '오늘 방문 후기',
                '여기 직원분들 진짜 친절하네요',
                '인테리어 리뉴얼 했나 봐요',
                '오랜만에 갔는데 메뉴가 바뀌었네요',
                '웨이팅 30분 기다린 후기',
                '주말에 가면 사람 엄청 많아요',
                '여기 단골인데 추천합니다',
                '근처에 새로 생긴 곳 다녀왔어요',
                '비 오는 날 방문기',
                '퇴근 후에 들렀다가 힐링',
                '가성비 최고인 곳 발견',
                '여기 분위기 좋아서 자주 가요',
                '주차장 넓어서 편해요',
                '아이랑 같이 가기 좋은 곳',
                '혼자 가기에도 부담 없어요',
                '저녁 시간대 방문 추천',
                '여기 사장님이 서비스를 잘 주셔요',
                '계절 메뉴 나왔길래 먹어봤어요',
                '재방문 의사 100%',
                '지인 추천으로 방문했어요'
            )
    END AS title,

    -- ── 내용: 타입별 현실적인 본문 ──
    CASE
        WHEN r_type < 0.45 THEN
            CONCAT(
                ELT(FLOOR(r_content * 8) + 1,
                    '안녕하세요! 오늘 방문해서 결제해봤는데 혜택이 잘 적용되더라구요.\n',
                    '여기 자주 이용하는 사람으로서 혜택 정보 공유합니다.\n',
                    '이번 달부터 혜택이 바뀌었길래 정리해봤어요.\n',
                    '카드사 앱 보다가 발견한 혜택인데요.\n',
                    '오늘 결제하고 나서 알게 된 팁 공유합니다.\n',
                    '이거 진작 알았으면 좋았을 텐데... 늦게라도 공유합니다.\n',
                    '가맹점 앱에서 쿠폰 받을 수 있어요.\n',
                    '혜택 꼼꼼히 따져본 결과 공유합니다.\n'
                ),
                ELT(FLOOR(r_content * 6) + 1,
                    '\n결제 금액 대비 적립률이 꽤 높은 편이에요. 특히 특정 카드를 사용하면 추가 할인도 받을 수 있습니다.',
                    '\n이번 달 프로모션이 꽤 괜찮아요. 일정 금액 이상 결제하면 추가 포인트를 줍니다.',
                    '\n주중이랑 주말 혜택이 다르니까 확인하고 가세요. 주중이 좀 더 이득입니다.',
                    '\n멤버십 등급이 높으면 추가 혜택이 있어요. 꾸준히 이용하시는 분들에게 유리합니다.',
                    '\n앱 결제를 하면 현장 결제보다 할인율이 더 높더라구요.',
                    '\n제휴 카드 쓰면 기본 할인에 추가 적립까지 돼서 꽤 이득이에요.'
                ),
                '\n\n도움이 되셨으면 좋겠습니다! 궁금한 점은 댓글로 남겨주세요.'
            )
        WHEN r_type < 0.75 THEN
            CONCAT(
                ELT(FLOOR(r_content * 8) + 1,
                    '안녕하세요, 질문이 있어서 글 올립니다.\n',
                    '혹시 이런 경험 있으신 분 계신가요?\n',
                    '검색해봐도 잘 안 나와서 여쭤봅니다.\n',
                    '처음 이용하는데 궁금한 게 있어서요.\n',
                    '고객센터 연결이 안 돼서 여기에 물어봅니다.\n',
                    '다른 분들 의견이 궁금합니다.\n',
                    '이용해보신 분들 답변 부탁드립니다.\n',
                    '급해서 글 올립니다. 아시는 분 계시면 답변 부탁드려요.\n'
                ),
                ELT(FLOOR(r_content * 6) + 1,
                    '\n오늘 결제를 했는데 할인이 적용되지 않았어요. 앱에서는 분명 쿠폰을 등록했는데 현장에서 적용이 안 되더라구요. 혹시 사용 조건이 따로 있는 건가요?',
                    '\n포인트가 적립이 안 됐는데 보통 며칠 정도 지나면 반영되나요? 3일째 안 들어오고 있어서 문의드립니다.',
                    '\n앱에서 보여주는 가격이랑 실제 결제 금액이 다른데 왜 그런 건지 아시는 분 계신가요?',
                    '\n카드 할인이랑 앱 쿠폰을 동시에 사용할 수 있는지 궁금합니다. 중복 할인이 되는 건가요?',
                    '\n환불 요청을 했는데 아직 처리가 안 됐어요. 보통 환불 처리 기간이 얼마나 걸리나요?',
                    '\n멤버십 등급 기준이 뭔지 정확히 아시는 분 계신가요? 앱에서 확인이 잘 안 돼서요.'
                ),
                '\n\n답변 주시면 감사하겠습니다!'
            )
        ELSE
            CONCAT(
                ELT(FLOOR(r_content * 8) + 1,
                    '오늘 다녀왔는데 후기 남겨봅니다.\n',
                    '지나가다 들렸는데 괜찮아서 글 써봅니다.\n',
                    '자주 가는 곳인데 오늘 특히 좋았어요.\n',
                    '처음 방문한 후기입니다.\n',
                    '친구 추천으로 갔다 왔어요.\n',
                    '주말에 시간 내서 다녀왔습니다.\n',
                    '퇴근길에 들렸다가 만족해서 글 남깁니다.\n',
                    '여기 자주 오는데 오늘 느낀 점 공유해요.\n'
                ),
                ELT(FLOOR(r_content * 6) + 1,
                    '\n전체적으로 서비스가 좋았어요. 직원분들도 친절하시고 시설도 깔끔합니다. 다음에 또 방문할 의향이 있어요.',
                    '\n가격 대비 만족스러웠습니다. 특별히 불만족스러운 점은 없었고 무난하게 좋았어요.',
                    '\n분위기가 좋아서 자주 오게 되는 곳이에요. 특히 평일 오후에 한적해서 좋습니다.',
                    '\n주차가 편하고 접근성이 좋아요. 대중교통으로도 오기 편하고 주차장도 넓습니다.',
                    '\n아이와 함께 방문했는데 키즈 공간도 있고 편의시설이 잘 되어 있어요.',
                    '\n단골로 다니는 곳인데 항상 일정한 퀄리티를 유지해줘서 신뢰가 갑니다.'
                ),
                '\n\n참고하세요!'
            )
    END AS content,

    -- ── 조회수: 로그정규분포 (현실적 바이럴 분포) ──
    -- 중앙값 ~50, 상위 1%가 수천 조회
    (@views := GREATEST(1, FLOOR(EXP(r_views_1 * 3.5 + r_views_2 * 2.5 - 1.5)))) AS view_count,

    -- ── 댓글 수: 조회수의 1~8% ──
    FLOOR(@views * (0.01 + r_comment_ratio * 0.07)) AS comment_count,

    -- ── 좋아요: 조회수의 3~15% ──
    FLOOR(@views * (0.03 + r_like_ratio * 0.12)) AS like_count,

    -- ── 신고 수: 대부분 0, 2% 확률로 1~5 ──
    IF(r_report < 0.02, FLOOR(r_report / 0.02 * 5) + 1, 0) AS report_count,

    -- ── 비활성 (신고 많은 글 ~1%) ──
    IF(r_inactive < 0.01, TRUE, FALSE) AS is_inactive,
    IF(r_inactive < 0.01,
        DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 30) DAY),
        NULL
    ) AS inactive_at,

    -- ── 소프트 삭제 ~5% ──
    IF(r_delete < 0.05, TRUE, FALSE) AS is_deleted,
    IF(r_delete < 0.05,
        DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 60) DAY),
        NULL
    ) AS deleted_at,

    -- ── 작성일: 최근 6개월, 최근일수록 밀도 높음 ──
    -- r_time_weight^2로 최근 날짜에 가중치
    (@created := DATE_SUB(NOW(), INTERVAL FLOOR(POWER(1 - r_time_weight, 2) * 180) DAY)
        + INTERVAL FLOOR(r_hour * 24) HOUR
        + INTERVAL FLOOR(r_minute * 60) MINUTE
    ) AS created_at,
    @created AS updated_at

FROM params;

-- ============================================================
-- 결과 확인
-- ============================================================
SELECT '=== 데이터 생성 완료 ===' AS status;
SELECT TIMESTAMPDIFF(SECOND, @start_time, NOW()) AS '소요시간(초)';

SELECT '카테고리' AS 테이블, COUNT(*) AS 건수 FROM category
UNION ALL
SELECT '가맹점', COUNT(*) FROM merchant
UNION ALL
SELECT '사용자', COUNT(*) FROM users
UNION ALL
SELECT '게시글', COUNT(*) FROM posts;

SELECT type AS 게시글타입, COUNT(*) AS 건수,
       ROUND(COUNT(*) * 100.0 / 50000, 1) AS '비율(%)'
FROM posts GROUP BY type;

SELECT
    ROUND(AVG(view_count)) AS '평균 조회수',
    ROUND(AVG(like_count)) AS '평균 좋아요',
    ROUND(AVG(comment_count)) AS '평균 댓글수',
    SUM(is_deleted) AS '삭제된 글',
    SUM(is_inactive) AS '비활성 글'
FROM posts;
