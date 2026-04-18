package com.payper.server.post.redis_v3;

import com.payper.server.post.repository.PostRepository;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostLikeRedisServiceV3 {
    private final StringRedisTemplate stringRedisTemplate;
    private final PostRepository postRepository;
    private final PostLikeReadRepository postLikeReadRepository;

    private static final Duration TTL = Duration.ofHours(24);
    private static final Duration INIT_LOCK_TTL = Duration.ofSeconds(5);

    /**
     * 좋아요 토글 (Redis only, DB 커넥션 0개)
     * 핫패스에서 호출됨
     */
    public void doPostLike(long userId, long postId) {
        // 1. 캐시 보장 (없으면 DB에서 lazy-load)
        ensureCached(postId);

        String bitmapKey = PostLikeRedisKeysV3.BITMAP_PREFIX + postId;
        String cntKey = PostLikeRedisKeysV3.CNT_PREFIX + postId;
        String postIdStr = String.valueOf(postId);

        // 2. SETBIT → oldBit 반환 (원자적)
        Boolean oldBit = stringRedisTemplate.opsForValue().setBit(bitmapKey, userId, true);

        // 3. 토글 판단
        if (Boolean.FALSE.equals(oldBit)) {
            // like: 비트가 0이었음 → 새 좋아요
            stringRedisTemplate.opsForValue().increment(cntKey);
        } else {
            // unlike: 비트가 1이었음 → 좋아요 취소
            stringRedisTemplate.opsForValue().setBit(bitmapKey, userId, false);
            stringRedisTemplate.opsForValue().decrement(cntKey);
        }

        // 4. TTL 갱신
        stringRedisTemplate.expire(bitmapKey, TTL);
        stringRedisTemplate.expire(cntKey, TTL);

        // 5. DB 동기화 대상 표시
        stringRedisTemplate.opsForSet().add(PostLikeRedisKeysV3.DIRTY, postIdStr);

        // 6. Hot100 랭킹 갱신
        updateRanking(postId);
    }

    public List<String> getPostTop100(){
        Set<String> result = stringRedisTemplate.opsForZSet().reverseRange(PostLikeRedisKeysV3.RANK, 0, 99);
        return result.stream().toList();
    }

    /**
     * 캐시 보장: cnt 키가 없으면 DB에서 lazy-load
     * SETNX 락으로 동시 초기화 방지
     */
    private void ensureCached(long postId) {
        String cntKey = PostLikeRedisKeysV3.CNT_PREFIX + postId;

        // 이미 캐시되어 있으면 패스
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(cntKey))) {
            return;
        }

        // 초기화 락 획득 시도
        String initLock = PostLikeRedisKeysV3.INIT_PREFIX + postId;
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(initLock, "1", INIT_LOCK_TTL);

        if (!Boolean.TRUE.equals(locked)) {
            // 다른 스레드가 초기화 중 → 잠시 대기 후 리턴
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return;
        }

        try {
            // 더블 체크
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(cntKey))) {
                return;
            }

            // DB에서 로드
            Long likeCount = postRepository.getLikeCount(postId);
            List<Long> userIds = postLikeReadRepository.findUserIdsByPostId(postId);

            // cnt 세팅
            stringRedisTemplate.opsForValue()
                    .set(cntKey, String.valueOf(likeCount != null ? likeCount : 0), TTL);

            // bitmap 세팅
            String bitmapKey = PostLikeRedisKeysV3.BITMAP_PREFIX + postId;
            for (Long uid : userIds) {
                stringRedisTemplate.opsForValue().setBit(bitmapKey, uid, true);
            }
            stringRedisTemplate.expire(bitmapKey, TTL);
        } finally {
            stringRedisTemplate.delete(initLock);
        }
    }

    /**
     * Hot100 랭킹 갱신
     * - 이미 RANK에 있으면: 점수 갱신
     * - 없으면: ZCARD < 100이면 추가 / 최하위보다 크면 교체
     */
    private void updateRanking(long postId) {
        String cntKey = PostLikeRedisKeysV3.CNT_PREFIX + postId;
        String postIdStr = String.valueOf(postId);

        String countStr = stringRedisTemplate.opsForValue().get(cntKey);
        if (countStr == null) return;
        long count = Long.parseLong(countStr);

        // 이미 랭킹에 있으면 점수만 갱신
        Double currentScore = stringRedisTemplate.opsForZSet()
                .score(PostLikeRedisKeysV3.RANK, postIdStr);
        if (currentScore != null) {
            stringRedisTemplate.opsForZSet()
                    .add(PostLikeRedisKeysV3.RANK, postIdStr, count);
            return;
        }

        // 랭킹에 없음 → 승격 판단
        Long size = stringRedisTemplate.opsForZSet().size(PostLikeRedisKeysV3.RANK);
        if (size == null) size = 0L;

        if (size < PostLikeRedisKeysV3.HOT_SIZE) {
            // 100개 미만이면 바로 추가
            stringRedisTemplate.opsForZSet()
                    .add(PostLikeRedisKeysV3.RANK, postIdStr, count);
        } else {
            // 최하위 점수 확인
            Set<ZSetOperations.TypedTuple<String>> min =
                    stringRedisTemplate.opsForZSet()
                            .rangeWithScores(PostLikeRedisKeysV3.RANK, 0, 0);
            if (min != null && !min.isEmpty()) {
                double minScore = min.iterator().next().getScore();
                if (count > minScore) {
                    // 최하위 제거 + 신규 추가
                    stringRedisTemplate.opsForZSet()
                            .removeRange(PostLikeRedisKeysV3.RANK, 0, 0);
                    stringRedisTemplate.opsForZSet()
                            .add(PostLikeRedisKeysV3.RANK, postIdStr, count);
                }
            }
        }
    }
}
