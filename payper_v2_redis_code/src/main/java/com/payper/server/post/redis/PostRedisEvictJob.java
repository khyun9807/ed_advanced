package com.payper.server.post.redis;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@EnableScheduling
// 48시간동안 좋아요 수가 업데이트 되지 않은 포스트 레디스 랭크와 활성에서 제거
public class PostRedisEvictJob {
    // 제거 대상은 배치가 처리하게 레디스 flush에다가 저장
    private final StringRedisTemplate stringRedisTemplate;

    // 1분마다 확인 후 제거
    @Scheduled(fixedDelay = 60_000)
    public void evict() {
        long now = System.currentTimeMillis();
        long cutoff = now - 48L * 60 * 60 * 1000;

        // 레디스 active 에서 비활성 포스트아디들 가져오기
        Set<String> expiredPostIds = stringRedisTemplate.opsForZSet().rangeByScore(PostLikeRedisKeys.ACTIVE, 0, cutoff);
        if (expiredPostIds == null || expiredPostIds.isEmpty()) return;

        for (String expiredPostId : expiredPostIds) {
            // 랭크, 활성에서 제거
            stringRedisTemplate.opsForZSet().remove(PostLikeRedisKeys.RANK, expiredPostId);
            stringRedisTemplate.opsForZSet().remove(PostLikeRedisKeys.ACTIVE, expiredPostId);
            stringRedisTemplate.delete(PostLikeRedisKeys.CNT_PREFIX + expiredPostId);

            // 디비 반영을 위한
            stringRedisTemplate.opsForSet().add(PostLikeRedisKeys.FLUSH, expiredPostId);
        }
    }
}
