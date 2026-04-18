package com.payper.server.post.cache;

import com.payper.server.global.exception.ApiException;
import com.payper.server.global.response.ErrorCode;
import com.payper.server.post.dto.PostResponse;
import com.payper.server.post.entity.Post;
import com.payper.server.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostCacheUtil {

    private final StringRedisTemplate stringRedisTemplate;
    private final PostRepository postRepository;
    private final ObjectMapper objectMapper;

    /** SETNX 재생성 락 */
    public boolean tryLock(Long postId) {
        return Boolean.TRUE.equals(
                stringRedisTemplate.opsForValue()
                        .setIfAbsent(PostCacheKeys.LOCK_PREFIX + postId, "1",
                                PostCacheKeys.LOCK_TTL_SECONDS, TimeUnit.SECONDS)
        );
    }

    public void saveToCache(Long postId, PostResponse.PostDetail detail) {
        try {
            long logicalTtl = jitteredLogicalTtl();
            long physicalTtl = logicalTtl + PostCacheKeys.PHYSICAL_TTL_BUFFER;
            long logicalExpireAt = System.currentTimeMillis() + (logicalTtl * 1000);

            CacheEntry entry = new CacheEntry(detail, logicalExpireAt);
            String json = objectMapper.writeValueAsString(entry);

            stringRedisTemplate.opsForValue()
                    .set(PostCacheKeys.DETAIL_PREFIX + postId, json,
                            physicalTtl, TimeUnit.SECONDS);
        } catch (JacksonException e) {
            log.warn("[PostCache] JSON 직렬화 실패 postId={}", postId, e);
        }
    }

    public void saveToCacheLegacy(Long postId, PostResponse.PostDetail detail) {
        try {
            long ttl = PostCacheKeys.DETAIL_TTL_SECONDS;

            String json = objectMapper.writeValueAsString(detail);

            stringRedisTemplate.opsForValue()
                    .set(PostCacheKeys.DETAIL_PREFIX + postId, json,
                            ttl, TimeUnit.SECONDS);
        } catch (JacksonException e) {
            log.warn("[PostCache] JSON 직렬화 실패 postId={}", postId, e);
        }
    }

    public CacheEntry getFromCache(Long postId) {
        String json = stringRedisTemplate.opsForValue()
                .get(PostCacheKeys.DETAIL_PREFIX + postId);
        if (json == null) return null;

        try {
            return objectMapper.readValue(json, CacheEntry.class);
        } catch (JacksonException e) {
            log.warn("[PostCache] JSON 역직렬화 실패 postId={}, 캐시 삭제", postId, e);
            evict(postId);
            return null;
        }
    }

    public PostResponse.PostDetail getFromCacheLegacy(Long postId) {
        String json = stringRedisTemplate.opsForValue()
                .get(PostCacheKeys.DETAIL_PREFIX + postId);
        if (json == null) return null;

        try {
            return objectMapper.readValue(json, PostResponse.PostDetail.class);
        } catch (JacksonException e) {
            log.warn("[PostCache] JSON 역직렬화 실패 postId={}, 캐시 삭제", postId, e);
            evict(postId);
            return null;
        }
    }

    /** 캐시 무효화 */
    public void evict(Long postId) {
        stringRedisTemplate.delete(PostCacheKeys.DETAIL_PREFIX + postId);
        log.info("[PostCache] evict postId={}", postId);
    }

    /** TTL Jitter: base ± 50% */
    private long jitteredLogicalTtl() {
        long base = PostCacheKeys.DETAIL_TTL_SECONDS;
        double jitter = base * PostCacheKeys.JITTER_RATIO;
        long offset = (long) (jitter * (Math.random() * 2 - 1));
        return base + offset;
    }

    public void incrementViewCount(Long postId) {
        stringRedisTemplate.opsForValue()
                .increment(PostCacheKeys.VIEW_COUNT_PREFIX + postId);
        stringRedisTemplate.opsForSet()
                .add(PostCacheKeys.VIEW_DIRTY, String.valueOf(postId));
    }
}
