package com.payper.server.post.redis_v3;

import com.payper.server.post.entity.Post;
import com.payper.server.post.repository.PostRepository;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

//@Component
@RequiredArgsConstructor
@Slf4j
public class PostLikeRedisSeedV3 implements ApplicationRunner {
    private final PostRepository postRepository;
    private final PostLikeReadRepository postLikeReadRepository;
    private final StringRedisTemplate stringRedisTemplate;

    private static final Duration TTL = Duration.ofHours(24);

    //@Override
    public void run(ApplicationArguments args) {
        seedRanking();
    }

    /** Hot100 ZSET + cnt + bitmap 시딩 (앱 시작 시 & Redis 복구 시 호출) */
    public void seedRanking() {
        log.info("v3 hot100 시딩 시작");

        // 기존 v3 rank 초기화
        stringRedisTemplate.delete(PostLikeRedisKeysV3.RANK);

        // DB에서 top100 게시글 조회
        List<Post> top100 = postRepository.findTop100ByLikeCount();
        if (top100.isEmpty()) {
            log.info("v3 시딩 대상 없음");
            return;
        }

        for (Post post : top100) {
            long postId = post.getId();
            long likeCount = post.getLikeCount();
            String postIdStr = String.valueOf(postId);
            String cntKey = PostLikeRedisKeysV3.CNT_PREFIX + postIdStr;
            String bitmapKey = PostLikeRedisKeysV3.BITMAP_PREFIX + postIdStr;

            // cnt 세팅
            stringRedisTemplate.opsForValue().set(cntKey, String.valueOf(likeCount), TTL);

            // RANK ZSET 추가
            stringRedisTemplate.opsForZSet().add(PostLikeRedisKeysV3.RANK, postIdStr, likeCount);

            // bitmap에 기존 좋아요 유저 세팅
            List<Long> userIds = postLikeReadRepository.findUserIdsByPostId(postId);
            for (Long userId : userIds) {
                stringRedisTemplate.opsForValue().setBit(bitmapKey, userId, true);
            }
            stringRedisTemplate.expire(bitmapKey, TTL);
        }

        log.info("v3 hot100 시딩 완료: {}개 게시글", top100.size());
    }
}
