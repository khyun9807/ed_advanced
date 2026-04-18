package com.payper.server.post.redis.outbox_to_redis;

import com.payper.server.post.repository.PostLikeOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@EnableScheduling
public class PostLikeOutboxRecoveryJob {
    private final PostLikeOutboxRepository postLikeOutboxRepository;

    @Scheduled(fixedDelay = 10_000)
    @Transactional
    public void recovery() {
        // processing 상태가 2분째인 outbox 상태 new로 갱신
        postLikeOutboxRepository.recoverStuck(120);
    }
}
