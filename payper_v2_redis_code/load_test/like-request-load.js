import http from "k6/http";
import { check, sleep } from "k6";
import exec from "k6/execution";
import { Counter } from "k6/metrics";

/**
 * ENV
 *  - BASE_URL: 예) http://15.134.x.x:8080
 *  - LIKE_PATH: 예) /api/v1/posts/200001/like
 *  - METHOD: 기본 POST
 *  - VUS: 기본 200
 *  - ITERATIONS: 기본 50000
 *
 *  - DISCARD_BODIES: (기본 false) true면 응답바디를 버림(성능↑, 디버깅↓)
 *  - LOG_FAIL_LIMIT: (기본 30) VU당 실패 로그 최대 출력 수
 *  - LOG_FAIL_BODY_CHARS: (기본 500) 실패 응답 바디 출력 길이
 */

const BASE_URL = (__ENV.BASE_URL || "http://localhost:8080").replace(/\/+$/, "");
const POST_ID = "200001";
const LIKE_PATH = __ENV.LIKE_PATH || `/api/v1/posts/${POST_ID}/like`;
const METHOD = (__ENV.METHOD || "POST").toUpperCase();

const VUS = parseInt(__ENV.VUS || "200", 10);
const ITERATIONS = parseInt(__ENV.ITERATIONS || "50000", 10);

const DISCARD_BODIES = String(__ENV.DISCARD_BODIES || "false").toLowerCase() === "true";
const LOG_FAIL_LIMIT = parseInt(__ENV.LOG_FAIL_LIMIT || "30", 10); // VU당
const LOG_FAIL_BODY_CHARS = parseInt(__ENV.LOG_FAIL_BODY_CHARS || "500", 10);

// ---- Custom metrics (Counter는 summary에서 values.count로 읽힘)
const users_total = new Counter("users_total");
const users_repeat_planned = new Counter("users_repeat_planned");

const like_requests_total = new Counter("like_requests_total");
const like_requests_ok = new Counter("like_requests_ok");
const like_requests_fail = new Counter("like_requests_fail");

const expected_likes_from_success = new Counter("expected_likes_from_success");

// repeat 유저의 1/2번째 요청 성공/실패 분리
const repeat_first_ok = new Counter("repeat_first_ok");
const repeat_first_fail = new Counter("repeat_first_fail");
const repeat_second_ok = new Counter("repeat_second_ok");
const repeat_second_fail = new Counter("repeat_second_fail");

// VU별 실패 로그 출력 제한(로그 폭발 방지)
let failLogPrinted = 0;

function joinUrl(base, path) {
    const p = path.startsWith("/") ? path : `/${path}`;
    return `${base}${p}`;
}

function pickHeaders(headers) {
    // 보고 싶은 헤더만 뽑아서 출력 (필요하면 더 추가)
    const keys = [
        "content-type",
        "content-length",
        "date",
        "server",
        "x-request-id",
        "x-correlation-id",
    ];
    const out = {};
    for (const k of keys) {
        if (headers && headers[k] !== undefined) out[k] = headers[k];
    }
    return out;
}

function likeRequest(userId, stepLabel) {
    const endpoint = joinUrl(BASE_URL, LIKE_PATH);
    const url = `${endpoint}?userId=${userId}`;

    // 초반 3번만 URL 확인
    const iter = exec.scenario.iterationInTest;
    if (iter < 3) console.log(`[debug] ${METHOD} ${url}`);

    like_requests_total.add(1);

    const res = http.request(METHOD, url, null, {
        tags: { name: "posts_like", step: stepLabel },
        timeout: "30s",
    });

    const ok = check(res, {
        "like status is 2xx": (r) => r.status >= 200 && r.status < 300,
    });

    if (ok) {
        like_requests_ok.add(1);
        return true;
    }

    // ---- 실패 처리
    like_requests_fail.add(1);

    // 실패 로그 출력(제한)
    if (failLogPrinted < LOG_FAIL_LIMIT) {
        failLogPrinted++;

        const body = (res && res.body != null) ? String(res.body) : "";
        const bodySnippet = body.slice(0, LOG_FAIL_BODY_CHARS);

        const hdr = pickHeaders(res.headers);
        const durationMs = res.timings?.duration;
        const remoteIp = res.remote_ip;

        console.error(
            [
                `[FAIL] step=${stepLabel} userId=${userId}`,
                `url=${url}`,
                `status=${res.status}`,
                `duration_ms=${durationMs}`,
                remoteIp ? `remote_ip=${remoteIp}` : null,
                `headers=${JSON.stringify(hdr)}`,
                `body_snippet=${JSON.stringify(bodySnippet)}`,
            ].filter(Boolean).join(" | ")
        );
    }

    return false;
}

export const options = {
    discardResponseBodies: DISCARD_BODIES,

    scenarios: {
        like_population: {
            executor: "shared-iterations",
            vus: VUS,
            iterations: ITERATIONS, // userId 1..ITERATIONS
            maxDuration: "30m",
        },
    },

    thresholds: {
        http_req_failed: ["rate<0.01"],
        http_req_duration: ["p(95)<500", "p(99)<1000"],
    },
};

export default function () {
    const iter = exec.scenario.iterationInTest; // 0..ITERATIONS-1
    const userId = iter + 1; // 1..ITERATIONS

    users_total.add(1);

    // ✅ userId 끝자리가 0 또는 5인 유저만 한번 더(정확히 20%)
    const lastDigit = userId % 10;
    const doRepeat = lastDigit === 0 || lastDigit === 5;
    if (doRepeat) users_repeat_planned.add(1);

    let okCount = 0;

    // 1회 요청
    const ok1 = likeRequest(userId, "first");
    if (ok1) okCount++;

    // repeat 유저는 1회 추가(토글)
    if (doRepeat) {
        if (ok1) repeat_first_ok.add(1);
        else repeat_first_fail.add(1);

        sleep(0.1);

        const ok2 = likeRequest(userId, "second");
        if (ok2) okCount++;

        if (ok2) repeat_second_ok.add(1);
        else repeat_second_fail.add(1);
    }

    // 유저가 중복되지 않으므로 합계 = “성공 기준 최종 좋아요 수”
    expected_likes_from_success.add(okCount % 2);
}

export function handleSummary(data) {
    const getCount = (name) => data.metrics?.[name]?.values?.count ?? 0;

    const U = getCount("users_total");
    const R = getCount("users_repeat_planned");
    const plannedExpectedLikes = U - R;

    const reqTotal = getCount("like_requests_total");
    const reqOk = getCount("like_requests_ok");
    const reqFail = getCount("like_requests_fail");

    const successBasedExpectedLikes = getCount("expected_likes_from_success");

    const rfOk = getCount("repeat_first_ok");
    const rfFail = getCount("repeat_first_fail");
    const rsOk = getCount("repeat_second_ok");
    const rsFail = getCount("repeat_second_fail");

    const lines = [];
    lines.push("===== LIKE LOADTEST EXPECTATION SUMMARY =====");
    lines.push(`POST_ID: ${POST_ID} (assumed initial likes = 0)`);
    lines.push(`BASE_URL: ${BASE_URL}`);
    lines.push(`LIKE_PATH: ${LIKE_PATH}`);
    lines.push(`METHOD: ${METHOD}`);
    lines.push(`VUS: ${VUS}`);
    lines.push(`ITERATIONS(users): ${ITERATIONS}`);
    lines.push(`DISCARD_BODIES: ${DISCARD_BODIES}`);
    lines.push("");
    lines.push(`[Planned] unique users = ${U} (userId 1..${ITERATIONS})`);
    lines.push(`[Planned] repeated users (last digit 0 or 5) = ${R}`);
    lines.push(`[Planned] expected final likes = ${plannedExpectedLikes}`);
    lines.push("");
    lines.push(`[Actual HTTP] total requests = ${reqTotal} (expected ~${Math.round(U * 1.2)})`);
    lines.push(`[Actual HTTP] 2xx requests = ${reqOk}, fail = ${reqFail}`);
    lines.push("");
    lines.push(`[Repeat breakdown] first_ok=${rfOk}, first_fail=${rfFail}, second_ok=${rsOk}, second_fail=${rsFail}`);
    lines.push(`[Success-based] expected final likes (toggle parity) = ${successBasedExpectedLikes}`);
    lines.push("");
    lines.push(
        `Hint: planned(=U-R)보다 final likes가 높으면 보통 repeat second_fail(취소 실패) 때문임.`
    );
    lines.push("=============================================");

    const out = lines.join("\n") + "\n";
    return {stdout: out, stderr: out, "like_summary.txt": out};
}