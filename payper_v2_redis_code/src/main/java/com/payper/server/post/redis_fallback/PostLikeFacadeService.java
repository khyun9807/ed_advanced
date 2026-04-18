package com.payper.server.post.redis_fallback;

import com.payper.server.post.post_like_legacy.PostLikeLegacyService;
import com.payper.server.post.redis_v3.PostLikeRedisServiceV3;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostLikeFacadeService {
    private final CircuitBreaker postLikeCircuitBreaker;
    private final PostLikeRedisServiceV3 redisService;
    private final PostLikeLegacyService legacyService;

    /** 좋아요 토글 */
    public void doPostLike(long userId, long postId) {
        try {
            postLikeCircuitBreaker.executeRunnable(
                    () -> redisService.doPostLike(userId, postId));
        } catch (CallNotPermittedException e) {
            // OPEN 상태 → DB fallback
            log.info("[Fallback] Circuit OPEN → DB 좋아요 처리 userId={}, postId={}", userId, postId);
            legacyService.doPostLikeLegacy(userId, postId);
        } catch (Exception e) {
            // Redis 호출 실패 (circuit breaker가 실패 기록 완료) → DB fallback
            log.warn("[Fallback] Redis 장애 → DB 좋아요 처리: {}", e.getMessage());
            legacyService.doPostLikeLegacy(userId, postId);
        }
    }

    /** TOP100 랭킹 조회 */
    public List<String> getPostTop100() {
        try {
            return postLikeCircuitBreaker.executeSupplier(
                    () -> redisService.getPostTop100());
        } catch (CallNotPermittedException e) {
            log.info("[Fallback] Circuit OPEN → DB TOP100 조회");
            return legacyFallbackTop100();
        } catch (Exception e) {
            log.warn("[Fallback] Redis 장애 → DB TOP100 조회: {}", e.getMessage());
            return legacyFallbackTop100();
        }
    }

    private List<String> legacyFallbackTop100() {
        return legacyService.getPostTop100Legacy()
                .stream()
                .map(post -> String.valueOf(post.id()))
                .toList();
    }
}
