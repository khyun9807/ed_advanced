package com.payper.server.post.redis.outbox_to_redis;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;

//@Component
@RequiredArgsConstructor
@EnableScheduling
@Slf4j
public class PostLikeOutboxWorkerPollingLegacy {
    private final PostLikeRedisFacade postLikeRedisFacade;

    // 현재 실행되는 워커 고유 아이디
    private final String workerId = "polling-worker-"+ UUID.randomUUID();

    // outbox들 -> redis 저장/동기화 작업
    //@Scheduled(fixedDelay = 200) // 0.2초 마다 한번
    public void work() {
        postLikeRedisFacade.handleOutboxesLegacy(workerId);
    }
}
