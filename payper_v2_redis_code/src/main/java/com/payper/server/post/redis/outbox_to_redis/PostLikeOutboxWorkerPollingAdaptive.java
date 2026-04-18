package com.payper.server.post.redis.outbox_to_redis;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostLikeOutboxWorkerPollingAdaptive implements ApplicationRunner {
    private final PostLikeOutboxProcessor outboxProcessor;
    private final PostLikeRedisFacade redisFacade;
    private final ThreadPoolTaskExecutor postLikeOutboxWorkerPollingExecutor;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final BlockingQueue<PostLikeOutbox> queue = new LinkedBlockingQueue<>(1000);
    private final String dispatcherId = "dispatcher-" + UUID.randomUUID();

    private static final int FETCH_LIMIT = 250;
    private static final int DONE_BATCH_SIZE = 50;

    @Override
    public void run(ApplicationArguments args) {
        // 1 dispatcher
        postLikeOutboxWorkerPollingExecutor.execute(this::dispatcherLoop);

        // N-1 workers
        int workerCount = postLikeOutboxWorkerPollingExecutor.getCorePoolSize() - 1;
        for (int i = 0; i < workerCount; i++) {
            postLikeOutboxWorkerPollingExecutor.execute(this::workerLoop);
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        postLikeOutboxWorkerPollingExecutor.shutdown();
    }

    // ── Dispatcher: DB에서 outbox 가져와 큐에 넣기 ──

    private void dispatcherLoop() {
        long sleepMs = 1_000;
        long minMs = 50;
        long maxMs = 30_000;
        double backoff = 2;

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                List<PostLikeOutbox> batch = outboxProcessor.fetchAndLock(dispatcherId, FETCH_LIMIT);

                if (!batch.isEmpty()) {
                    for (PostLikeOutbox item : batch) {
                        queue.put(item); // 큐가 가득 차면 블로킹
                    }
                    sleepMs = minMs;
                } else {
                    sleepMs = Math.min(maxMs, (long) (sleepMs * backoff));
                }

                Thread.sleep(sleepMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("dispatcher 오류", e);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    // ── Worker: 큐에서 꺼내 Redis 처리 후 배치 markDone ──

    private void workerLoop() {
        List<String> doneIds = new ArrayList<>();

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                PostLikeOutbox item = queue.poll(1, TimeUnit.SECONDS);

                if (item == null) {
                    flushDone(doneIds);
                    continue;
                }

                try {
                    processOne(item);
                    doneIds.add(item.getId());
                } catch (Exception e) {
                    // 실패한 항목은 doneIds에 넣지 않음
                    // recovery job이 PROCESSING → NEW로 복구
                    log.error("outbox 처리 실패 id={}", item.getId(), e);
                }

                if (doneIds.size() >= DONE_BATCH_SIZE || queue.isEmpty()) {
                    flushDone(doneIds);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                flushDone(doneIds);
                break;
            }
        }
    }

    private void processOne(PostLikeOutbox outbox) {
        int delta = outbox.getDelta();
        if (delta == 0) return;

        long postId = outbox.getPostId();
        String outboxId = outbox.getId();
        long now = System.currentTimeMillis();

        if (redisFacade.isHot(postId)) {
            redisFacade.applyHotDelta(outboxId, postId, delta, now);
        } else {
            Long likeCount = outboxProcessor.updateColdPostAndGetCount(postId, delta);
            if (likeCount != null) {
                redisFacade.tryRankUp(outboxId, postId, likeCount, now);
            }
        }
    }

    private void flushDone(List<String> doneIds) {
        if (doneIds.isEmpty()) return;
        try {
            outboxProcessor.completeBatch(new ArrayList<>(doneIds));
        } catch (Exception e) {
            log.error("batch complete 실패, size={}", doneIds.size(), e);
        }
        doneIds.clear();
    }
}
