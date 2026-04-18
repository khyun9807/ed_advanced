import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ──────────────────────────────────────────────
// 커스텀 메트릭
// ──────────────────────────────────────────────
const errorRate = new Rate('error_rate');
const likeDuration = new Trend('like_duration', true);
const top100Duration = new Trend('top100_duration', true);
const likeCount = new Counter('like_requests');
const top100Count = new Counter('top100_requests');

// ──────────────────────────────────────────────
// 설정 (test-data-seed.sql 기준)
//   게시글 50,000건, 사용자 500명
//   좋아요 = INSERT IGNORE 토글 (DB 직접 처리)
//   TOP100 = ORDER BY like_count DESC LIMIT 100
// ──────────────────────────────────────────────
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USER_COUNT = 500;
const POST_COUNT = 50000;

// 사용자 ID 범위 (test-data-seed.sql에서 생성된 ID)
// ※ 환경에 따라 @user_base가 다를 수 있음. 기본값 = 1
const USER_ID_BASE = parseInt(__ENV.USER_ID_BASE || '1');

// ──────────────────────────────────────────────
// 부하 시나리오
//
// 시나리오 1: like_traffic
//   - 사용자들이 게시글에 좋아요를 누르는 메인 트래픽
//   - 인기 게시글에 좋아요가 집중되는 현실적 분포
//   - INSERT IGNORE + DELETE + UPDATE 3개 쿼리가 매 요청마다 발생
//
// 시나리오 2: top100_reads
//   - 인기 게시글 랭킹을 조회하는 읽기 트래픽
//   - ORDER BY like_count DESC LIMIT 100 풀스캔 쿼리
//   - 좋아요 쓰기 트래픽과 동시에 발생 (락 경합)
//
// 타임라인:
//   0:00 ~ 0:15   워밍업 (30 VU 좋아요 + 5 VU 랭킹)
//   0:15 ~ 0:30   증가 (→ 200 VU 좋아요 + 20 VU 랭킹)
//   0:30 ~ 1:30   피크 (500 VU 좋아요 + 50 VU 랭킹)
//   1:30 ~ 2:30   유지 (300 VU 좋아요 + 30 VU 랭킹)
//   2:30 ~ 3:00   정리 (→ 0)
// ──────────────────────────────────────────────
export const options = {
    scenarios: {
        like_traffic: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '15s', target: 30 },    // 워밍업
                { duration: '15s', target: 50 },   // 증가
                { duration: '1m',  target: 100 },   // 피크
                { duration: '1m',  target: 100 },   // 유지
                { duration: '30s', target: 0 },     // 정리
            ],
            gracefulRampDown: '10s',
            exec: 'likeTraffic',
        },
        top100_reads: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '15s', target: 30 },     // 워밍업
                { duration: '15s', target: 50 },    // 증가
                { duration: '1m',  target: 100 },    // 피크
                { duration: '1m',  target: 100 },    // 유지
                { duration: '30s', target: 0 },     // 정리
            ],
            gracefulRampDown: '10s',
            exec: 'top100Traffic',
        },
    },

    thresholds: {
        like_duration: ['p(95)<500', 'p(99)<1000'],
        top100_duration: ['p(95)<500', 'p(99)<1000'],
        error_rate: ['rate<0.05'],
    },
};

// ──────────────────────────────────────────────
// 헬퍼 함수
// ──────────────────────────────────────────────

/**
 * 좋아요 대상 게시글 ID 선택 (현실적 분포)
 *
 * 사용자는 대부분 인기 게시글에 좋아요를 누른다 (파레토 법칙).
 *   - 40%: 상위 인기 게시글 (1~100)     ← 이미 좋아요 많은 게시글
 *   - 30%: 준인기 게시글 (101~1000)     ← 떠오르는 게시글
 *   - 20%: 일반 게시글 (1001~10000)     ← 최근 글 위주
 *   - 10%: 롱테일 (10001~50000)         ← 드문 좋아요
 */
function pickPostId() {
    const r = Math.random();
    if (r < 0.40) {
        return Math.floor(Math.random() * 100) + 1;
    } else if (r < 0.70) {
        return Math.floor(Math.random() * 900) + 101;
    } else if (r < 0.90) {
        return Math.floor(Math.random() * 9000) + 1001;
    } else {
        return Math.floor(Math.random() * 40000) + 10001;
    }
}

/**
 * 사용자 ID 선택 (활동적 사용자 집중 분포)
 *
 * 소수의 사용자가 대부분의 좋아요를 누른다.
 *   - 50%: 핵심 활동 사용자 (1~50명, 전체의 10%)
 *   - 30%: 일반 활동 사용자 (51~200명)
 *   - 20%: 비활동적 사용자 (201~500명)
 */
function pickUserId() {
    const r = Math.random();
    let offset;
    if (r < 0.50) {
        offset = Math.floor(Math.random() * 50);             // 1~50
    } else if (r < 0.80) {
        offset = 50 + Math.floor(Math.random() * 150);       // 51~200
    } else {
        offset = 200 + Math.floor(Math.random() * 300);      // 201~500
    }
    return USER_ID_BASE + offset;
}

// ──────────────────────────────────────────────
// 시나리오 실행 함수
// ──────────────────────────────────────────────

/**
 * 좋아요 토글 트래픽
 *
 * 레거시 로직:
 *   1) INSERT IGNORE INTO post_like (좋아요 시도)
 *   2) 실패 시 → DELETE (좋아요 취소)
 *   3) post.like_count += delta (UPDATE)
 *
 * 동시 500 VU가 같은 인기 게시글에 좋아요를 누르면
 * → 동일 row에 UPDATE 경합 → 락 대기 → 응답 지연
 */
export function likeTraffic() {
    const postId = pickPostId();
    const userId = pickUserId();

    const url = `${BASE_URL}/api/v1/posts/${postId}/like/polling?userId=${userId}`;
    const res = http.get(url, { tags: { name: 'POST_like' } });

    const success = check(res, {
        'like: status 200': (r) => r.status === 200,
    });

    errorRate.add(!success);
    likeDuration.add(res.timings.duration);
    likeCount.add(1);

    // 사용자 행동 시뮬레이션:
    // 좋아요 누른 후 잠깐 다른 게시글 둘러보는 시간 (0.5~2초)
    sleep(0.5 + Math.random() * 1.5);
}

/**
 * TOP100 랭킹 조회 트래픽
 *
 * 레거시 로직:
 *   SELECT * FROM posts ORDER BY like_count DESC LIMIT 100
 *
 * 좋아요 UPDATE와 동시에 실행되면:
 * → like_count 인덱스 없으면 테이블 풀스캔
 * → 쓰기 락과 읽기 경합
 */
export function top100Traffic() {
    const url = `${BASE_URL}/api/v1/posts/top/legacy`;
    const res = http.get(url, { tags: { name: 'GET_top100' } });

    const success = check(res, {
        'top100: status 200': (r) => r.status === 200,
        'top100: has data': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.data && body.data.length > 0;
            } catch { return false; }
        },
    });

    errorRate.add(!success);
    top100Duration.add(res.timings.duration);
    top100Count.add(1);

    // 랭킹 페이지 조회 후 둘러보는 시간 (2~5초)
    sleep(2 + Math.random() * 3);
}

// ──────────────────────────────────────────────
// 결과 요약
// ──────────────────────────────────────────────
export function handleSummary(data) {
    const likeDur   = data.metrics.like_duration?.values || {};
    const top100Dur = data.metrics.top100_duration?.values || {};
    const err       = data.metrics.error_rate?.values || {};
    const likes     = data.metrics.like_requests?.values || {};
    const top100s   = data.metrics.top100_requests?.values || {};

    const fmt = (v) => v !== undefined ? v.toFixed(1) : 'N/A';

    const testDurationSec = (data.state?.testRunDurationMs || 1) / 1000;
    const totalLikes = likes.count || 0;
    const totalTop100 = top100s.count || 0;
    const totalReqs = totalLikes + totalTop100;

    const summary = `
══════════════════════════════════════════════════
  게시글 좋아요 부하테스트
══════════════════════════════════════════════════
  테스트 대상:           DB 직접 처리 (INSERT IGNORE + UPDATE)
  사용자 수:             ${USER_COUNT}명 (가중 분포)
  게시글 수:             ${POST_COUNT}건 (인기 게시글 집중)
──────────────────────────────────────────────────
  총 요청 수:            ${totalReqs}
    - 좋아요 토글:       ${totalLikes}
    - TOP100 조회:       ${totalTop100}
  테스트 시간:           ${testDurationSec.toFixed(1)}s
  RPS (req/sec):         ${(totalReqs / testDurationSec).toFixed(1)}
──────────────────────────────────────────────────
  [좋아요 토글 응답 시간]
    중앙값:              ${fmt(likeDur.med)} ms
    p90:                 ${fmt(likeDur['p(90)'])} ms
    p95:                 ${fmt(likeDur['p(95)'])} ms
    p99:                 ${fmt(likeDur['p(99)'])} ms
    max:                 ${fmt(likeDur.max)} ms
  [TOP100 조회 응답 시간]
    중앙값:              ${fmt(top100Dur.med)} ms
    p90:                 ${fmt(top100Dur['p(90)'])} ms
    p95:                 ${fmt(top100Dur['p(95)'])} ms
    p99:                 ${fmt(top100Dur['p(99)'])} ms
    max:                 ${fmt(top100Dur.max)} ms
──────────────────────────────────────────────────
  에러율:                ${err.rate !== undefined ? (err.rate * 100).toFixed(2) : 'N/A'}%
══════════════════════════════════════════════════

  [병목 포인트 해석 가이드]
  - 좋아요 p99 > 100ms  → 인기 게시글 row 락 경합 (UPDATE posts SET like_count)
  - 좋아요 p99 > 500ms  → 커넥션 풀 고갈 가능성
  - TOP100 중앙값 > 50ms → 인덱스 미사용, 테이블 풀스캔 의심
  - TOP100 p99 > 500ms  → 쓰기 트래픽과의 락 경합
  - 에러율 > 1%          → 데드락 또는 커넥션 타임아웃 발생
`;

    return {
        stdout: summary,
        'k6/results/post-like-legacy-result.json': JSON.stringify(data, null, 2),
    };
}
