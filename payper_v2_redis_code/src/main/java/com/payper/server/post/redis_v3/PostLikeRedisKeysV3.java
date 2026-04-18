package com.payper.server.post.redis_v3;

public class PostLikeRedisKeysV3 {
    private PostLikeRedisKeysV3() {}

    // 좋아요 누른 유저 비트맵 (BITMAP: userId를 bit offset으로 사용)
    public static final String BITMAP_PREFIX = "post:like:v3:bitmap:";

    // 게시글별 좋아요 수 (STRING)
    public static final String CNT_PREFIX = "post:like:v3:cnt:";

    // DB 동기화 대상 게시글 (SET)
    public static final String DIRTY = "post:like:v3:dirty";

    // Hot100 랭킹 (ZSET: postId → likeCount)
    public static final String RANK = "post:like:v3:rank";

    // lazy-load 중복 방지 락 (STRING, TTL 5s)
    public static final String INIT_PREFIX = "post:like:v3:init:";

    public static final int HOT_SIZE = 100;
}
