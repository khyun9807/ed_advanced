package com.payper.server.post.redis.redis_to_db;

import com.payper.server.post.redis.PostLikeRedisKeys;
import com.payper.server.post.repository.PostRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@EnableScheduling
public class PostLikeOutboxSyncJob { // 레디스->디비로 동기화
    private final StringRedisTemplate stringRedisTemplate;
    private final InnerPostLikeCountUpdater innerPostLikeCountUpdater;

    @Scheduled(cron = "0 * * * * *") // 1분 마다 동기화 ==
    public void sync() {
        // 동기화할 포스트 아이디들 저장공간
        List<String> postIds = new ArrayList<>();

        // 레디스에서 동기화해야하는 포스트 아이디 추출
        List<String> dirty = stringRedisTemplate.opsForSet().pop(PostLikeRedisKeys.DIRTY, 500);
        if (dirty != null) postIds.addAll(dirty);
        List<String> flush = stringRedisTemplate.opsForSet().pop(PostLikeRedisKeys.FLUSH, 500);
        if (flush != null) postIds.addAll(flush);
        // 제거는 포스트라이크아웃박스워커에서 trim

        // 동기화할 포스트 아이디들이 없다면
        if (postIds.isEmpty()) return;

        // 레디스에서 현재 라이크수를 읽어서 디비에 저장
        Map<Long, Long> likeCounts = new HashMap<>();
        for (String postId : postIds) {
            String value = stringRedisTemplate.opsForValue().get(PostLikeRedisKeys.CNT_PREFIX + postId);
            if (value == null) continue;

            likeCounts.put(Long.parseLong(postId), Long.parseLong(value));
        }

        // 실제로 동기화
        innerPostLikeCountUpdater.batchUpdate(likeCounts);
    }
}

@Component
@RequiredArgsConstructor
class InnerPostLikeCountUpdater {
    private final PostRepository postRepository;

    @Transactional
    public void batchUpdate(Map<Long, Long> map) {
        for (var e : map.entrySet()) {
            postRepository.setLikeCount(e.getKey(), e.getValue());
        }
    }
}
