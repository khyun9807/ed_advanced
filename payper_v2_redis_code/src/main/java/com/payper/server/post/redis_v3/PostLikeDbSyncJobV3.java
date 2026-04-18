package com.payper.server.post.redis_v3;

import com.payper.server.post.repository.PostRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

//@Component
@RequiredArgsConstructor
@EnableScheduling
@Slf4j
public class PostLikeDbSyncJobV3 {
    private final StringRedisTemplate stringRedisTemplate;
    private final PostLikeDbSyncInnerV3 innerUpdater;

    //@Scheduled(fixedDelay = 5000) // 5초 간격
    public void sync() {
        try {
            log.info("Syncing post like db");
            // dirty에서 동기화 대상 추출
            List<String> postIds = stringRedisTemplate.opsForSet()
                    .pop(PostLikeRedisKeysV3.DIRTY, 500);
            if (postIds == null || postIds.isEmpty()) return;

            // Redis에서 현재 count 읽기
            Map<Long, Long> likeCounts = new HashMap<>();
            for (String postId : postIds) {
                String value = stringRedisTemplate.opsForValue()
                        .get(PostLikeRedisKeysV3.CNT_PREFIX + postId);
                if (value == null) continue;
                likeCounts.put(Long.parseLong(postId), Long.parseLong(value));
            }

            if (likeCounts.isEmpty()) return;

            // DB 배치 업데이트
            innerUpdater.batchUpdate(likeCounts);
        } catch (Exception e) {
            log.warn("[DbSyncJob] 동기화 실패 (다음 주기에 재시도): {}", e.getMessage());
            // 예외를 삼켜서 ScheduledExecutorService가 태스크를 중단하지 않도록 함
        }
    }
}

//@Component
@RequiredArgsConstructor
@Slf4j
class PostLikeDbSyncInnerV3 {
    private final PostRepository postRepository;

    @Transactional
    public void batchUpdate(Map<Long, Long> likeCounts) {

        for (var entry : likeCounts.entrySet()) {
            postRepository.setLikeCount(entry.getKey(), entry.getValue());
        }

    }
}
