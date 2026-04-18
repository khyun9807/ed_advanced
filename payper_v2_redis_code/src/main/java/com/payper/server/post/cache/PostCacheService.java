package com.payper.server.post.cache;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.payper.server.global.exception.ApiException;
import com.payper.server.global.response.ErrorCode;
import com.payper.server.post.dto.PostResponse;
import com.payper.server.post.entity.Post;
import com.payper.server.post.repository.PostRepository;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final PostCacheUtil postCacheUtil;
    private final PostCacheDBUtil postCacheDBUtil;
    private final PostRepository postRepository;

    // ── 공개 메서드 ──

    /** 게시글 상세 조회 (Look-Aside 캐시 + 조회수 INCR) */
    //@Transactional(readOnly = true)
    public PostResponse.PostDetail getPostDetail(Long postId) {

        CacheEntry entry = postCacheUtil.getFromCache(postId);

        PostResponse.PostDetail result;
        if (entry == null) {
            result = postCacheDBUtil.handlePhysicalMiss(postId);
        } else if (!entry.isStale()) {
            //log.debug("[PostCache] HIT postId={}", postId);
            result = entry.getData();
        } else {
            handleStale(postId);
            result=entry.getData();
        }

        postCacheUtil.incrementViewCount(postId);
        return result;
    }

    public PostResponse.PostDetail getPostDetailLegacy(Long postId) {

        PostResponse.PostDetail fromCacheDetail = postCacheUtil.getFromCacheLegacy(postId);

        PostResponse.PostDetail result;
        if (fromCacheDetail == null) {
            result= PostResponse.PostDetail.from(postRepository
                    .findByIdAndIsDeletedFalse(postId)
                    .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND)));
            postCacheUtil.saveToCacheLegacy(postId, result);
        } else {
            result=fromCacheDetail;
        }

        postCacheUtil.incrementViewCount(postId);
        return result;
    }

    /** 캐시 무효화 */
    public void evict(Long postId) {
        stringRedisTemplate.delete(PostCacheKeys.DETAIL_PREFIX + postId);
        log.info("[PostCache] evict postId={}", postId);
    }

    /** 논리적 만료 (STALE) — 데이터는 있지만 논리적 TTL 초과 */
    private void handleStale(Long postId) {
        if (postCacheUtil.tryLock(postId)) {
            //log.info("[PostCache] STALE postId={}, 락 획득 → 비동기 재생성", postId);
            // 비동기로 캐시 재생성 (현재 스레드는 stale 즉시 반환)
            /*CompletableFuture.runAsync(() -> {
                try {
                    PostResponse.PostDetail fresh = loadFromDb(postId);
                    saveToCache(postId, fresh);
                } catch (Exception e) {
                    log.warn("[PostCache] 비동기 재생성 실패 postId={}", postId, e);
                }
            });*/
            postCacheDBUtil.postDbToRedis(postId);
        } else {
            //log.debug("[PostCache] STALE postId={}, stale 반환", postId);
        }
    }
}
