package com.payper.server.post.redis;

public class PostLikeRedisKeys {
    public PostLikeRedisKeys() {}

    // Hot100 랭킹(점수=likeCount)
    public static final String RANK = "post:like:rank:hot100";
    // 48시간이 지났거나 그동안 좋아요 수 없으면 방출을 위함
    public static final String ACTIVE = "post:like:active:hot100";
    // 1분 마다 redis->post 디비에 동기화할 대상들
    public static final String DIRTY = "post:like:dirty";
    // 100위를 벗어난 게시물 redis->post 디비에 동기화할 대상들
    public static final String FLUSH = "post:like:flush";
    // 핫100 좋아요 수 기록
    public static final String CNT_PREFIX = "post:like:cnt:";
    // outbox->redis 반영 중복 처리 방지
    public static final String DEDUP_PREFIX = "post:like:dedup:";
    // 승강/방출 할 때 접근 보호용
    public static final String RANK_LOCK = "post:like:rank:lock";

    public static final int HOT_SIZE = 100;

    // like수에 대한 outbox commit 후 워커가 동작할 수 있게 메시지를 주는 메시지 브로커
    public static final String STREAM = "post:like:messages";

    // like수에 대한 outbox commit 후 워커가 동작할 수 있게 메시지를 받는 그룹
    // 메시지가 오면 그 때 outboxe들 -> redis 처리하는 워커 동작
    public static final String GROUP = "post-like-message-receivers";
}
