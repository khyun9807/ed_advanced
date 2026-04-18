package com.payper.server.post.cache;

public class PostCacheKeys {
    private PostCacheKeys() {}

    // 게시글 상세 캐시 (STRING: JSON, TTL 10분)
    public static final String DETAIL_PREFIX = "post:cache:detail:";

    // 게시글 조회수 카운터 (STRING)
    public static final String VIEW_COUNT_PREFIX = "post:view:count:";

    // DB 동기화 대상 게시글 ID (SET)
    public static final String VIEW_DIRTY = "post:view:dirty";

    // 논리적 캐시 TTL (초)
    public static final long DETAIL_TTL_SECONDS = 30;

    // 물리적 TTL 버퍼 — 논리적 만료 후에도 stale 데이터를 유지하는 기간 (초)
    //public static final long PHYSICAL_TTL_BUFFER = 30;
    public static final long PHYSICAL_TTL_BUFFER = 30;

    // 캐시 재생성 락 (STRING, SETNX)
    public static final String LOCK_PREFIX = "post:cache:lock:";

    // 재생성 락 TTL (초)
    public static final long LOCK_TTL_SECONDS = 5;

    // TTL Jitter 비율 (±50%)
    public static final double JITTER_RATIO = 0.5;
}
