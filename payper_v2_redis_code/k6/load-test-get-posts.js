import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ──────────────────────────────────────────────
// 커스텀 메트릭
// ──────────────────────────────────────────────
const errorRate = new Rate('error_rate');
const postListDuration = new Trend('post_list_duration', true);
const requestCount = new Counter('total_requests');

// ──────────────────────────────────────────────
// 설정 (test-data-seed.sql 기준)
//   가맹점 100개, 게시글 50,000건
//   인기 가맹점 상위 20개에 게시글 60% 집중
// ──────────────────────────────────────────────
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const MERCHANT_BASE = Number(__ENV.MERCHANT_BASE || 1);
const MERCHANT_COUNT = 100;

// 인기 가맹점 (상위 20개) vs 일반 가맹점 (나머지 80개)
const HOT_MERCHANTS = [];
for (let i = 0; i < 20; i++) HOT_MERCHANTS.push(MERCHANT_BASE + i);

const NORMAL_MERCHANTS = [];
for (let i = 20; i < MERCHANT_COUNT; i++) NORMAL_MERCHANTS.push(MERCHANT_BASE + i);

// ──────────────────────────────────────────────
// 부하 시나리오
// ──────────────────────────────────────────────
export const options = {
    scenarios: {
        // 1) 워밍업 + 일반 트래픽
        normal_traffic: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 50 },
                { duration: '2m', target: 50 },
                { duration: '30s', target: 0 },
            ],
            gracefulRampDown: '10s',
            exec: 'normalBrowsing',
        },

        // 2) 피크 트래픽: 점심/퇴근 시간대
        peak_traffic: {
            executor: 'ramping-vus',
            startVUs: 0,
            startTime: '3m30s',
            stages: [
                { duration: '20s', target: 100 },
                { duration: '1m', target: 200 },
                { duration: '2m', target: 200 },
                { duration: '30s', target: 50 },
                { duration: '20s', target: 0 },
            ],
            gracefulRampDown: '10s',
            exec: 'normalBrowsing',
        },

        // 3) 스파이크: 혜택 공지 직후 폭발적 트래픽
        spike_test: {
            executor: 'ramping-vus',
            startVUs: 0,
            startTime: '8m',
            stages: [
                { duration: '5s', target: 300 },
                { duration: '30s', target: 300 },
                { duration: '15s', target: 50 },
                { duration: '30s', target: 0 },
            ],
            gracefulRampDown: '10s',
            exec: 'spikeBrowsing',
        },
    },

    thresholds: {
        http_req_duration: [
            'p(95)<500',
            'p(99)<1000',
        ],
        error_rate: ['rate<0.01'],
        post_list_duration: ['p(95)<400'],
    },
};

// ──────────────────────────────────────────────
// 헬퍼 함수
// ──────────────────────────────────────────────

function weightedRandom(items) {
    const total = items.reduce((sum, item) => sum + item.weight, 0);
    let rand = Math.random() * total;
    for (const item of items) {
        rand -= item.weight;
        if (rand <= 0) return item.value;
    }
    return items[items.length - 1].value;
}

/** 가맹점 ID: 인기 가맹점에 70% 트래픽 집중 */
function randomMerchantId() {
    if (Math.random() < 0.7) {
        return HOT_MERCHANTS[Math.floor(Math.random() * HOT_MERCHANTS.length)];
    }
    return NORMAL_MERCHANTS[Math.floor(Math.random() * NORMAL_MERCHANTS.length)];
}

/**
 * 페이지 번호 — 대부분 첫 페이지
 *  60% → page 0
 *  15% → page 1
 *  10% → page 2
 *  10% → page 3~9
 *   5% → page 10~49 (딥 페이지네이션)
 */
function realisticPageNumber() {
    return weightedRandom([
        { value: 0, weight: 60 },
        { value: 1, weight: 15 },
        { value: 2, weight: 10 },
        { value: Math.floor(Math.random() * 7) + 3, weight: 10 },
        { value: Math.floor(Math.random() * 40) + 10, weight: 5 },
    ]);
}

function realisticPageSize() {
    return weightedRandom([
        { value: 10, weight: 80 },
        { value: 20, weight: 15 },
        { value: 30, weight: 5 },
    ]);
}

function realisticPostType() {
    return weightedRandom([
        { value: null, weight: 40 },        // 전체
        { value: 'BENEFIT', weight: 35 },    // 혜택 (가장 인기)
        { value: 'QUESTION', weight: 15 },   // 질문
        { value: 'ETC', weight: 10 },        // 기타
    ]);
}

function realisticSort() {
    return weightedRandom([
        { value: { sort: 'POSTING_DATE', direction: 'DESC' }, weight: 55 },
        { value: { sort: 'LIKE_COUNT', direction: 'DESC' }, weight: 20 },
        { value: { sort: 'VIEW_COUNT', direction: 'DESC' }, weight: 15 },
        { value: { sort: 'COMMENT_COUNT', direction: 'DESC' }, weight: 10 },
    ]);
}

// ──────────────────────────────────────────────
// 요청 빌더 — 게시글 목록 조회 전용
// ──────────────────────────────────────────────

/**
 * 사용자 행동 패턴 (목록 조회만)
 *
 * A (40%): 특정 가맹점 진입 → 최신 게시글 목록 (기본 정렬)
 * B (20%): 특정 가맹점 → 혜택(BENEFIT) 필터
 * C (15%): 전체 게시글 인기순 탐색 (정렬 랜덤)
 * D (10%): 특정 가맹점 → 타입 랜덤 필터 + 정렬 랜덤
 * E (10%): 특정 가맹점에서 페이지 넘기기 (딥 스크롤)
 * F  (5%): 전체 게시글 홈 피드 (필터 없음)
 */
function buildRequest() {
    const pattern = weightedRandom([
        { value: 'A', weight: 40 },
        { value: 'B', weight: 20 },
        { value: 'C', weight: 15 },
        { value: 'D', weight: 10 },
        { value: 'E', weight: 10 },
        { value: 'F', weight: 5 },
    ]);

    const params = {};

    switch (pattern) {
        case 'A': // 특정 가맹점 최신 게시글 (서버 기본 정렬)
            params.merchantId = randomMerchantId();
            params.page = 0;
            params.size = 10;
            break;

        case 'B': // 특정 가맹점 + 혜택 필터
            params.merchantId = randomMerchantId();
            params.type = 'BENEFIT';
            params.page = 0;
            params.size = realisticPageSize();
            break;

        case 'C': // 전체 게시글 인기순
            Object.assign(params, realisticSort());
            params.page = realisticPageNumber();
            params.size = 10;
            break;

        case 'D': // 특정 가맹점 + 타입 랜덤 + 정렬 랜덤
            params.merchantId = randomMerchantId();
            const type = realisticPostType();
            if (type) params.type = type;
            Object.assign(params, realisticSort());
            params.page = 0;
            params.size = realisticPageSize();
            break;

        case 'E': // 특정 가맹점 딥 페이지네이션
            params.merchantId = randomMerchantId();
            params.page = realisticPageNumber();
            params.size = realisticPageSize();
            Object.assign(params, realisticSort());
            break;

        case 'F': // 홈 피드 (전체, 기본 정렬)
            params.page = 0;
            params.size = 10;
            break;
    }

    return { pattern, params };
}

function buildUrl(params) {
    const query = Object.entries(params)
        .filter(([_, v]) => v !== null && v !== undefined)
        .map(([k, v]) => `${k}=${encodeURIComponent(v)}`)
        .join('&');
    return `${BASE_URL}/api/v1/posts${query ? '?' + query : ''}`;
}

// ──────────────────────────────────────────────
// 응답 검증
// ──────────────────────────────────────────────

function validateResponse(res) {
    return check(res, {
        'status is 200': (r) => r.status === 200,
        'body is not empty': (r) => r.body && r.body.length > 0,
        'valid JSON': (r) => {
            try { JSON.parse(r.body); return true; } catch { return false; }
        },
        'API success': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.status === 'SUCCESS' || body.status === 200;
            } catch { return false; }
        },
    });
}

// ──────────────────────────────────────────────
// 시나리오 실행 함수
// ──────────────────────────────────────────────

/** 일반 브라우징 — 목록 조회 + 확률적 다음 페이지 */
export function normalBrowsing() {
    const { params } = buildRequest();
    const url = buildUrl(params);

    const res = http.get(url, { tags: { name: 'GET_posts' } });
    const success = validateResponse(res);
    errorRate.add(!success);
    postListDuration.add(res.timings.duration);
    requestCount.add(1);

    // 목록 스캔 시간 (1~5초)
    sleep(1 + Math.random() * 4);

    // 30% 확률로 다음 페이지 (스크롤)
    if (Math.random() < 0.3) {
        const nextParams = { ...params, page: (params.page || 0) + 1 };
        const nextUrl = buildUrl(nextParams);
        const nextRes = http.get(nextUrl, { tags: { name: 'GET_posts_next' } });

        errorRate.add(nextRes.status !== 200);
        postListDuration.add(nextRes.timings.duration);
        requestCount.add(1);

        sleep(1 + Math.random() * 3);

        // 10% 확률로 한 페이지 더 (열심히 스크롤하는 사용자)
        if (Math.random() < 0.1) {
            const moreParams = { ...params, page: (params.page || 0) + 2 };
            const moreUrl = buildUrl(moreParams);
            const moreRes = http.get(moreUrl, { tags: { name: 'GET_posts_next' } });

            errorRate.add(moreRes.status !== 200);
            postListDuration.add(moreRes.timings.duration);
            requestCount.add(1);

            sleep(1 + Math.random() * 2);
        }
    }
}

/** 스파이크 — 인기 가맹점 혜택 목록에 트래픽 집중 */
export function spikeBrowsing() {
    // 상위 3개 가맹점에 75% 트래픽 극도 집중
    const hotIds = HOT_MERCHANTS.slice(0, 3);
    const isHotRequest = Math.random() < 0.75;

    if (isHotRequest) {
        const merchantId = hotIds[Math.floor(Math.random() * hotIds.length)];
        const params = {
            merchantId,
            page: realisticPageNumber(),
            size: 10,
        };
        // 80% 혜택 필터, 20% 전체
        if (Math.random() < 0.8) params.type = 'BENEFIT';

        const url = buildUrl(params);
        const res = http.get(url, { tags: { name: 'GET_posts_spike' } });

        const success = validateResponse(res);
        errorRate.add(!success);
        postListDuration.add(res.timings.duration);
        requestCount.add(1);

        // 스파이크 시 빠르게 확인 (0.5~2초)
        sleep(0.5 + Math.random() * 1.5);

        // 40% 확률로 다음 페이지 (관심이 높아서 더 탐색)
        if (Math.random() < 0.4) {
            const nextParams = { ...params, page: (params.page || 0) + 1 };
            const nextUrl = buildUrl(nextParams);
            const nextRes = http.get(nextUrl, { tags: { name: 'GET_posts_spike_next' } });

            errorRate.add(nextRes.status !== 200);
            postListDuration.add(nextRes.timings.duration);
            requestCount.add(1);

            sleep(0.5 + Math.random() * 1);
        }
    } else {
        // 나머지 25%는 일반 브라우징
        normalBrowsing();
    }
}

// ──────────────────────────────────────────────
// 결과 요약
// ──────────────────────────────────────────────
export function handleSummary(data) {
    const httpDur = data.metrics.http_req_duration?.values || {};
    const listDur = data.metrics.post_list_duration?.values || {};
    const err = data.metrics.error_rate?.values || {};
    const total = data.metrics.total_requests?.values || {};

    const fmt = (v) => v !== undefined ? v.toFixed(1) : 'N/A';

    const summary = `
══════════════════════════════════════════════════
  게시글 목록 조회 부하테스트 결과 요약
══════════════════════════════════════════════════
  총 요청 수:              ${total.count || 'N/A'}
──────────────────────────────────────────────────
  [HTTP 전체]
    중앙값:                ${fmt(httpDur.med)} ms
    p95:                   ${fmt(httpDur['p(95)'])} ms
    p99:                   ${fmt(httpDur['p(99)'])} ms
  [게시글 목록 API]
    중앙값:                ${fmt(listDur.med)} ms
    p95:                   ${fmt(listDur['p(95)'])} ms
    p99:                   ${fmt(listDur['p(99)'])} ms
──────────────────────────────────────────────────
  에러율:                  ${err.rate !== undefined ? (err.rate * 100).toFixed(2) : 'N/A'}%
══════════════════════════════════════════════════
`;

    return {
        stdout: summary,
        'k6/results/load-test-result.json': JSON.stringify(data, null, 2),
    };
}
