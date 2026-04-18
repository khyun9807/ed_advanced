import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';

// ──────────────────────────────────────────────
// 커스텀 메트릭
// ──────────────────────────────────────────────
const errorRate = new Rate('error_rate');
const detailDuration = new Trend('post_detail_duration', true);
const requestCount = new Counter('total_requests');
const missCount = new Counter('cache_miss_requests');   // 500ms 이상 = MISS로 추정

// ──────────────────────────────────────────────
// 설정
//   TTL = 30초, loadFromDb = 500ms 지연
//   게시글 4개에만 집중
// ──────────────────────────────────────────────
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TARGET_POST_IDS = [1, 2, 3, 4];
const CACHE_TTL = 30; // 서버 TTL과 동일 (초)

// ──────────────────────────────────────────────
// 시나리오 설계
//
// 캐시 스탬피드를 체감하려면:
//   1. 캐시를 워밍업한다 (4개 게시글 캐시 생성)
//   2. TTL 만료 직전까지 소규모 트래픽 유지
//   3. TTL 만료 시점에 대량 VU를 한꺼번에 투입
//   4. 수백 개 요청이 동시에 MISS → 500ms 지연 DB 조회
//
// 타임라인 (TTL=30초 기준):
//   0:00 ~ 0:05   워밍업 (5 VU, 캐시 생성)
//   0:05 ~ 0:25   유지 (5 VU, 캐시 HIT 확인)
//   0:25 ~ 0:30   폭발 준비 (→ 500 VU로 급증)
//   0:30 ~ 0:35   ★ 1차 스탬피드 (TTL 만료, 500 VU 동시 MISS)
//   0:35 ~ 0:55   유지 (500 VU, 캐시 재생성 후 HIT)
//   0:55 ~ 1:00   ★ 2차 스탬피드 (TTL 다시 만료)
//   1:00 ~ 1:20   유지 (500 VU)
//   1:20 ~ 1:25   ★ 3차 스탬피드
//   1:25 ~ 1:40   정리 (→ 0 VU)
// ──────────────────────────────────────────────
export const options = {
    scenarios: {
        stampede: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                // Phase 1: 워밍업 — 캐시 생성
                { duration: '5s',  target: 5 },

                // Phase 2: 소규모 유지 — 캐시 HIT 상태 확인
                { duration: '20s', target: 5 },

                // Phase 3: TTL 만료 직전 급증 — 스탬피드 유발
                { duration: '5s',  target: 500 },

                // Phase 4: 1차 스탬피드 후 유지
                { duration: '25s', target: 500 },

                // Phase 5: 2차 스탬피드 (자동으로 TTL 만료)
                { duration: '25s', target: 500 },

                // Phase 6: 3차 스탬피드
                { duration: '25s', target: 500 },

                // Phase 7: 정리
                { duration: '15s', target: 0 },
            ],
            gracefulRampDown: '5s',
            exec: 'stampede',
        },
    },

    thresholds: {
        http_req_duration: ['p(95)<2000'],
        error_rate: ['rate<0.05'],
    },
};

// ──────────────────────────────────────────────
// 실행 함수
// ──────────────────────────────────────────────
export function stampede() {
    // 4개 게시글 중 균등하게 선택
    const postId = TARGET_POST_IDS[Math.floor(Math.random() * TARGET_POST_IDS.length)];

    const url = `${BASE_URL}/api/v1/posts/${postId}`;
    //const url = `${BASE_URL}/api/v1/posts/legacy/${postId}`;
    const res = http.get(url, { tags: { name: 'GET_post_detail' } });

    const success = check(res, {
        'status is 200': (r) => r.status === 200,
    });

    errorRate.add(!success);
    detailDuration.add(res.timings.duration);
    requestCount.add(1);

    // 500ms 이상 걸리면 캐시 MISS로 추정 (DB에서 500ms sleep)
    if (res.timings.duration >= 400) {
        missCount.add(1);
    }

    // 짧은 간격으로 연속 요청 (스탬피드 압력 극대화)
    sleep(0.1 + Math.random() * 0.3);
}

// ──────────────────────────────────────────────
// 결과 요약
// ──────────────────────────────────────────────
export function handleSummary(data) {
    const httpDur = data.metrics.http_req_duration?.values || {};
    const detDur  = data.metrics.post_detail_duration?.values || {};
    const err     = data.metrics.error_rate?.values || {};
    const total   = data.metrics.total_requests?.values || {};
    const misses  = data.metrics.cache_miss_requests?.values || {};

    const fmt = (v) => v !== undefined ? v.toFixed(1) : 'N/A';

    const testDurationSec = (data.state?.testRunDurationMs || 1) / 1000;
    const totalReqs = total.count || 0;
    const totalMisses = misses.count || 0;
    const missRate = totalReqs > 0 ? ((totalMisses / totalReqs) * 100).toFixed(2) : 'N/A';

    const summary = `
══════════════════════════════════════════════════
  캐시 스탬피드 부하테스트
══════════════════════════════════════════════════
  대상 게시글:           ${TARGET_POST_IDS.join(', ')}
  캐시 TTL:              ${CACHE_TTL}초
  DB 조회 지연:          500ms (시뮬레이션)
──────────────────────────────────────────────────
  총 요청 수:            ${totalReqs}
  테스트 시간:           ${testDurationSec.toFixed(1)}s
  RPS (req/sec):         ${(totalReqs / testDurationSec).toFixed(1)}
──────────────────────────────────────────────────
  [응답 시간]
    중앙값:              ${fmt(detDur.med)} ms
    p90:                 ${fmt(detDur['p(90)'])} ms
    p95:                 ${fmt(detDur['p(95)'])} ms
    p99:                 ${fmt(detDur['p(99)'])} ms
    max:                 ${fmt(detDur.max)} ms
──────────────────────────────────────────────────
  [캐시 스탬피드 지표]
    캐시 MISS 추정:      ${totalMisses}건 (>= 400ms 응답)
    MISS 비율:           ${missRate}%
    ※ TTL 만료 시점에 MISS가 집중되면 스탬피드 발생
──────────────────────────────────────────────────
  에러율:                ${err.rate !== undefined ? (err.rate * 100).toFixed(2) : 'N/A'}%
══════════════════════════════════════════════════

  [해석 가이드]
  - 캐시 HIT 시 응답: ~1~5ms (Redis GET)
  - 캐시 MISS 시 응답: ~500ms+ (Thread.sleep 500ms)
  - p99가 500ms 이상이면 스탬피드로 인한 DB 부하 발생
  - MISS 비율이 높을수록 스탬피드가 심각한 것
`;

    return {
        stdout: summary,
        'k6/results/cache-stampede-result.json': JSON.stringify(data, null, 2),
    };
}
