import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ──────────────────────────────────────────────
// 커스텀 메트릭
// ──────────────────────────────────────────────
const errorRate = new Rate('error_rate');
const detailDuration = new Trend('post_detail_duration', true);
const requestCount = new Counter('total_requests');

// ──────────────────────────────────────────────
// 설정 (test-data-seed.sql 기준)
//   게시글 50,000건, 그중 1~10번이 현재 핫한 게시글
// ──────────────────────────────────────────────
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const POST_COUNT = 50000;

// 핫 게시글 (트래픽 집중 대상)
const HOT_POST_IDS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

// ──────────────────────────────────────────────
// 부하 시나리오
//
// 타임라인:
//   0:00 ~ 1:00   평소 트래픽 (30 VU)
//   1:00 ~ 1:30   핫 게시글 소문이 퍼지기 시작 (→ 150 VU)
//   1:30 ~ 4:00   피크 폭주 (300 VU 유지)
//   4:00 ~ 5:00   점차 빠짐 (→ 50 VU)
//   5:00 ~ 5:30   잔여 트래픽 (→ 0)
// ──────────────────────────────────────────────
export const options = {
    scenarios: {
        // 2) 피크 폭주: 핫 게시글에 트래픽 집중
        peak_hot: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '15s', target: 150 },   // 소문 퍼짐
                { duration: '30s', target: 1500 },    // 피크 도달
                { duration: '1m', target: 1500 },     // 피크 유지
                { duration: '30s', target: 50 },      // 점차 빠짐
                { duration: '15s', target: 0 },      // 종료
            ],
            gracefulRampDown: '10s',
            exec: 'hotDetail',
        },
    },

    thresholds: {
        http_req_duration: [
            'p(95)<500',
            'p(99)<1000',
        ],
        error_rate: ['rate<0.01'],
        post_detail_duration: ['p(95)<300'],
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

/**
 * 핫 게시글 ID 선택 (균등하지 않음)
 * - 1~3번: 가장 핫함 (60%)
 * - 4~7번: 상당히 핫함 (30%)
 * - 8~10번: 덜 핫함 (10%)
 */
function hotPostId() {
    return weightedRandom([
        { value: HOT_POST_IDS[Math.floor(Math.random() * 3)], weight: 60 },         // 1~3
        { value: HOT_POST_IDS[3 + Math.floor(Math.random() * 4)], weight: 30 },     // 4~7
        { value: HOT_POST_IDS[7 + Math.floor(Math.random() * 3)], weight: 10 },     // 8~10
    ]);
}

/**
 * 일반 게시글 ID 선택 (최근 글에 집중)
 * - 60%: 최근 게시글 (40001~50000)
 * - 30%: 중간 게시글 (20001~40000)
 * - 10%: 오래된 게시글 (1~20000)
 */
function normalPostId() {
    const r = Math.random();
    if (r < 0.6) {
        return Math.floor(POST_COUNT * 0.8 + Math.random() * POST_COUNT * 0.2) + 1;
    } else if (r < 0.9) {
        return Math.floor(POST_COUNT * 0.4 + Math.random() * POST_COUNT * 0.4) + 1;
    } else {
        return Math.floor(Math.random() * POST_COUNT * 0.4) + 1;
    }
}

/** 응답 검증 */
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

/** 평소 트래픽: 다양한 게시글을 고르게 조회 */
export function normalDetail() {
    // 10% 확률로 핫 게시글도 섞임 (소문 퍼지기 전에도 소수 유입)
    const postId = Math.random() < 0.1 ? hotPostId() : normalPostId();

    const url = `${BASE_URL}/api/v1/posts/${postId}`;
    const res = http.get(url, { tags: { name: 'GET_post_detail' } });

    const success = validateResponse(res);
    errorRate.add(!success);
    detailDuration.add(res.timings.duration);
    requestCount.add(1);

    // 읽는 시간 (2~6초)
    sleep(2 + Math.random() * 4);
}

/** 피크 폭주: 핫 게시글에 극도로 집중 */
export function hotDetail() {
    // 85% 핫 게시글, 15% 일반 게시글 (핫 게시글 보다가 다른 글도 눌러봄)
    const postId = Math.random() < 0.85 ? hotPostId() : normalPostId();

    const url = `${BASE_URL}/api/v1/posts/${postId}`;
    const res = http.get(url, { tags: { name: 'GET_post_detail_hot' } });

    const success = validateResponse(res);
    errorRate.add(!success);
    detailDuration.add(res.timings.duration);
    requestCount.add(1);

    // 핫 게시글은 빠르게 읽고 나감 (0.5~3초)
    // 일반 게시글은 좀 더 천천히 (2~5초)
    if (postId <= 10) {
        sleep(0.5 + Math.random() * 2.5);
    } else {
        sleep(2 + Math.random() * 3);
    }

    // 40% 확률로 다른 핫 게시글도 연속 조회 (SNS 공유 타고 들어와서 여러 개 봄)
    if (Math.random() < 0.4) {
        const nextPostId = hotPostId();
        const nextUrl = `${BASE_URL}/api/v1/posts/${nextPostId}`;
        const nextRes = http.get(nextUrl, { tags: { name: 'GET_post_detail_hot' } });

        errorRate.add(nextRes.status !== 200);
        detailDuration.add(nextRes.timings.duration);
        requestCount.add(1);

        sleep(0.5 + Math.random() * 2);
    }
}

// ──────────────────────────────────────────────
// 결과 요약
// ──────────────────────────────────────────────
export function handleSummary(data) {
    const httpDur = data.metrics.http_req_duration?.values || {};
    const httpReqs = data.metrics.http_reqs?.values || {};
    const detDur = data.metrics.post_detail_duration?.values || {};
    const err = data.metrics.error_rate?.values || {};
    const total = data.metrics.total_requests?.values || {};

    const fmt = (v) => v !== undefined ? v.toFixed(1) : 'N/A';

    // 테스트 총 시간 (초)
    const testDurationSec = (data.state?.testRunDurationMs || 1) / 1000;
    const totalReqs = total.count || 0;
    const rps = (httpReqs.rate || (totalReqs / testDurationSec)).toFixed(1);
    const tps = rps; // 1 request = 1 transaction

    const summary = `
══════════════════════════════════════════════════
  게시글 상세 조회 부하테스트 (핫 게시글 시나리오)
══════════════════════════════════════════════════
  총 요청 수:              ${totalReqs}
  테스트 시간:             ${testDurationSec.toFixed(1)}s
  RPS (req/sec):           ${rps}
  TPS (tx/sec):            ${tps}
  Peak RPS:                ${fmt(httpReqs.rate)} (k6 평균)
──────────────────────────────────────────────────
  [HTTP 전체]
    중앙값:                ${fmt(httpDur.med)} ms
    p95:                   ${fmt(httpDur['p(95)'])} ms
    p99:                   ${fmt(httpDur['p(99)'])} ms
    max:                   ${fmt(httpDur.max)} ms
  [게시글 상세 API]
    중앙값:                ${fmt(detDur.med)} ms
    p95:                   ${fmt(detDur['p(95)'])} ms
    p99:                   ${fmt(detDur['p(99)'])} ms
    max:                   ${fmt(detDur.max)} ms
──────────────────────────────────────────────────
  에러율:                  ${err.rate !== undefined ? (err.rate * 100).toFixed(2) : 'N/A'}%
══════════════════════════════════════════════════
`;

    return {
        stdout: summary,
        'k6/results/load-test-detail-result.json': JSON.stringify(data, null, 2),
    };
}
