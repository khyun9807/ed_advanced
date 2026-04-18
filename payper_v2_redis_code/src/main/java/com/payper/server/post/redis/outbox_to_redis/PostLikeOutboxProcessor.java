package com.payper.server.post.redis.outbox_to_redis;

import com.payper.server.post.repository.PostLikeOutboxRepository;
import com.payper.server.post.repository.PostRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PostLikeOutboxProcessor {
    private final PostLikeOutboxRepository postLikeOutboxRepository;
    private final PostRepository postRepository;

    // Phase 1: outbox 가져오기 + processing 표시 (짧은 TX)
    @Transactional
    public List<PostLikeOutbox> fetchAndLock(String workerId, int limit) {
        List<PostLikeOutbox> outboxes = postLikeOutboxRepository.findNewForProcessing(limit);
        if (outboxes.isEmpty()) return outboxes;

        for (PostLikeOutbox o : outboxes) {
            o.markProcessing(workerId);
        }
        postLikeOutboxRepository.saveAll(outboxes);
        return outboxes;
    }

    // cold post: 좋아요수 DB 갱신 + 현재 좋아요수 조회 (짧은 TX)
    @Transactional
    public Long updateColdPostAndGetCount(long postId, int delta) {
        postRepository.addLikeCount(postId, delta);
        return postRepository.getLikeCount(postId);
    }

    // Phase 3: 처리 완료 배치 표시 (짧은 TX)
    @Transactional
    public void completeBatch(List<String> outboxIds) {
        if (outboxIds.isEmpty()) return;
        postLikeOutboxRepository.markDoneByIds(outboxIds);
    }
}
