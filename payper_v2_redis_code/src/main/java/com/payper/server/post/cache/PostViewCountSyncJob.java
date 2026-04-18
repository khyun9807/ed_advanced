package com.payper.server.post.cache;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

//@Component
@RequiredArgsConstructor
@Slf4j
public class PostViewCountSyncJob {

    private final StringRedisTemplate stringRedisTemplate;
    private final PostViewCountSyncInner innerUpdater;

    //@Scheduled(fixedDelay = 30_000) // 30초 간격
    public void sync() {
        List<String> postIds = stringRedisTemplate.opsForSet()
                .pop(PostCacheKeys.VIEW_DIRTY, 500);
        if (postIds == null || postIds.isEmpty()) return;

        Map<Long, Long> viewCounts = new HashMap<>();
        for (String postId : postIds) {
            String value = stringRedisTemplate.opsForValue()
                    .get(PostCacheKeys.VIEW_COUNT_PREFIX + postId);
            if (value == null) continue;
            viewCounts.put(Long.parseLong(postId), Long.parseLong(value));
        }

        if (viewCounts.isEmpty()) return;

        innerUpdater.batchUpdate(viewCounts);
        log.info("[ViewCountSync] DB 반영 완료 {}건", viewCounts.size());
    }
}

//@Component
@RequiredArgsConstructor
@Slf4j
class PostViewCountSyncInner {

    private final PostCacheRepository postCacheRepository;

    @Transactional
    public void batchUpdate(Map<Long, Long> viewCounts) {
        for (var entry : viewCounts.entrySet()) {
            postCacheRepository.setViewCount(entry.getKey(), entry.getValue());
        }
    }
}
