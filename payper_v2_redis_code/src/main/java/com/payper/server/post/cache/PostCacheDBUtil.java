package com.payper.server.post.cache;

import com.payper.server.global.exception.ApiException;
import com.payper.server.global.response.ErrorCode;
import com.payper.server.post.dto.PostResponse;
import com.payper.server.post.entity.Post;
import com.payper.server.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
public class PostCacheDBUtil {
    private final PostCacheUtil postCacheUtil;
    private final PostRepository postRepository;

    //@Async
    @Transactional(readOnly = true)
    public void postDbToRedis(Long postId) {
        PostResponse.PostDetail fresh = loadFromDb(postId);
        postCacheUtil.saveToCache(postId, fresh);
    }

    @Transactional(readOnly = true)
    /** 물리적 MISS — Redis에 키 자체가 없는 경우 */
    public PostResponse.PostDetail handlePhysicalMiss(Long postId) {
        //log.info("[PostCache] MISS postId={}", postId);

        if (postCacheUtil.tryLock(postId)) {
            // 락 획득: DB 조회 → 캐시 저장 → 반환
            PostResponse.PostDetail detail = loadFromDb(postId);
            postCacheUtil.saveToCache(postId, detail);
            return detail;
        }

        // 락 실패: 다른 스레드가 재생성 중 → 잠시 대기 후 재시도
        try { Thread.sleep(510); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        CacheEntry retryEntry = postCacheUtil.getFromCache(postId);
        if (retryEntry != null) {
            return retryEntry.getData();
        }

        // 여전히 없으면: 직접 DB 조회 (캐시 저장 X)
        return loadFromDb(postId);
    }

    private PostResponse.PostDetail loadFromDb(Long postId) {
        // [스탬피드 테스트용] 무거운 쿼리 시뮬레이션 (500ms 지연)
        try { Thread.sleep(500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }

        Post post = postRepository.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
        return PostResponse.PostDetail.from(post);
    }
}
