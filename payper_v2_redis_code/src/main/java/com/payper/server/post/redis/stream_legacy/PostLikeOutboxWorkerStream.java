package com.payper.server.post.redis.stream_legacy;

import com.payper.server.post.redis.PostLikeRedisKeys;
import com.payper.server.post.redis.outbox_to_redis.PostLikeRedisFacade;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// @Component
@RequiredArgsConstructor
@Slf4j
public class PostLikeOutboxWorkerStream { // implements ApplicationRunner {
    private final StringRedisTemplate stringRedisTemplate;
    private final PostLikeRedisFacade postLikeRedisFacade;
    private final ThreadPoolTaskExecutor postLikeOutboxWorkerStreamExecutor;

    // 메시지가 분사될 수 있게 다른 컨슈머 이름 지정
    private final String receiverName = "receiver-" + UUID.randomUUID();

    // 락 역할
    private final AtomicBoolean running = new AtomicBoolean(true);

    // @Override
    public void run(ApplicationArguments args) throws Exception {
        // 메인이 아닌 별도 스레드에서 워커 루프를 진행
        postLikeOutboxWorkerStreamExecutor.execute(() -> {
            loop();
        });
    }

    // @PreDestroy
    public void stop() {
        // 앱 종료시 중단
        running.set(false);
        postLikeOutboxWorkerStreamExecutor.shutdown();
    }

    private void loop() {
        while (running.get() && !(Thread.currentThread().isInterrupted())) {
            // 워커는 계속 동작 중이며 stream 메시지를 지속적으로 소비
            try {
                // 한번에 최대 200개 읽기
                // 메시지 없으면 10초 동안 블록. 10초 동안 뭐 없으면 빈값 반환
                StreamReadOptions blockOptions =
                        StreamReadOptions.empty().count(200).block(Duration.ofSeconds(10));
                Consumer receiver = Consumer.from(PostLikeRedisKeys.GROUP, receiverName);
                // 그룹이 마지막으로 소비한 지점 이후부터 읽기
                StreamOffset<String> streamOffset =
                        StreamOffset.create(PostLikeRedisKeys.STREAM, ReadOffset.lastConsumed());

                // 메시지를 맵 형태로 받기
                // <키, 필드면, 필드값>
                List<@NonNull MapRecord<String, Object, Object>> messages =
                        stringRedisTemplate.opsForStream().read(receiver, blockOptions, streamOffset);

                // 처리할 메세지 없으면 다시 처음부터
                if (messages == null || messages.isEmpty()) continue;

                // 메시지 순차 처리
                for (MapRecord<String, Object, Object> message : messages) {
                    try { // 실제 처리(아웃박스 -> 레디스)
                        postLikeRedisFacade.handleOneStreamMessage(message);

                        // 해당 메시지 처리 성공시 ACK 처리
                        // ACK처리 해줘야 해당 메시지 다시 안 받아옴.
                        stringRedisTemplate
                                .opsForStream()
                                .acknowledge(PostLikeRedisKeys.STREAM, PostLikeRedisKeys.GROUP, message.getId());
                    } catch (Exception e) { // 실패시 ack하지 않아서
                        // 다시 받을 수 있게
                        log.error("failed messageId={}", message.getId(), e);
                    }
                }
            } catch (Exception e) { // redis 장애, 네트워크 장애 등.
                // 재시도 조절
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
