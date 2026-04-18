package com.payper.server.post.redis.stream_legacy;

import com.payper.server.post.redis.PostLikeRedisKeys;
import com.payper.server.post.redis.outbox_to_redis.PostLikeOutbox;
import com.payper.server.post.repository.PostLikeOutboxRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

//@Component
@RequiredArgsConstructor
// 레디스 스트림에 메시지 넣는 컴포넌트
// 메시지에 outboxId만 포함되도 좋지만, 디버깅을 위해 postId, delta도
public class PostLikeMessagePublisher {
    private final StringRedisTemplate stringRedisTemplate;
    private final PostLikeOutboxRepository postLikeOutboxRepository;

    public void publish(String outboxId, long postId, int delta) {
        // Stream에 넣을 메시지의 payload (필드/값)
        Map<String, String> messageBody = Map.of(
                "outboxId", outboxId,
                "postId", String.valueOf(postId),
                "delta", String.valueOf(delta));

        // 스트림에 다가 메시지 넣어주면
        // 잠들었던 워커가 메시지를 받아서
        // 아웃박스 내용 -> 레디스에 반영 후
        // 아웃박스 상태 DONE 저장
        stringRedisTemplate.opsForStream().add(MapRecord.create(PostLikeRedisKeys.STREAM, messageBody));
    }

    // new로 남아있는 아웃 박스들 오래 된거부터 재발행
    // 느린 주기로 누락된|스트림 발행이 실패한 아웃박스 처리
    // @Scheduled(fixedDelay = 60_000)
    @Transactional(readOnly = true)
    public void republish() {
        List<PostLikeOutbox> missingOutboxes = postLikeOutboxRepository.findTop200ByStatusOrderByCreatedAtAsc("NEW");

        for (PostLikeOutbox outbox : missingOutboxes) {
            if (outbox.getDelta() != 0) {
                publish(outbox.getId(), outbox.getPostId(), outbox.getDelta());
            }
        }
    }
}
