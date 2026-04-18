package com.payper.server.post.service;

import com.payper.server.post.redis.outbox_to_redis.PostLikeOutbox;
import com.payper.server.post.redis.outbox_to_redis.PostLikeRedisFacade;
import com.payper.server.post.redis.stream_legacy.PostLikeMessagePublisher;
import com.payper.server.post.repository.PostLikeOutboxRepository;
import com.payper.server.post.repository.PostLikeRepository;
import com.payper.server.post.repository.PostRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostLikeService {
    // private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostLikeOutboxRepository postLikeOutboxRepository;
    private final PostLikeRedisFacade postLikeRedisFacade;
    //private final PostLikeMessagePublisher postLikeMessagePublisher;

    @Transactional
    public void doPostLikePolling(Long userId, Long postId) {
        // 일단 사용자-게시물 관계 넣기 시도
        int inserted = postLikeRepository.insertIgnore(postId, userId);

        // 좋아요 수 변화값
        int delta = 0;
        if (inserted > 0) {
            // 라이크
            delta += inserted;
        } else {
            // 언라이크
            // 실패시 이미 들어있기에 제거
            int deleted = postLikeRepository.deleteByPostIdAndUserId(postId, userId);
            if (deleted > 0) {
                delta -= deleted;
            }
        }

        // 둘다 실패시 좋아요 수 변화값 0

        // post-like 테이블->레디스로 반영하기 위한 아웃박스 저장
        // 아웃박스의 내용은 스케줄러에 의해 레디스로 반영됨
        // 핫한, 차가운 게시판의 좋아요 수에 대한 아웃박스 모두 저장
        UUID outboxId = UUID.randomUUID();
        postLikeOutboxRepository.save(PostLikeOutbox.newEvent(outboxId, postId, userId, delta));

        /*// 핫 게시판과 다르게 차가운 게시판시 바로 post 테이블에 즉시 업데이트
        if (delta != 0 && !postLikeRedisFacade.isHot(postId)) {
            postRepository.addLikeCount(postId, delta);
        }*/
    }

    /*@Transactional
    public void doPostLikeStream(Long userId, Long postId) {
        // 일단 사용자-게시물 관계 넣기 시도
        int inserted = postLikeRepository.insertIgnore(postId, userId);

        // 좋아요 수 변화값
        int delta = 0;
        if (inserted > 0) {
            // 라이크
            delta += inserted;
        } else {
            // 언라이크
            // 실패시 이미 들어있기에 제거
            int deleted = postLikeRepository.deleteByPostIdAndUserId(postId, userId);
            if (deleted > 0) {
                delta -= deleted;
            }
        }

        // 둘다 실패시 좋아요 수 변화값 0

        // post-like 테이블->레디스로 반영하기 위한 아웃박스 저장
        // 아웃박스의 내용은 스케줄러에 의해 레디스로 반영됨
        // 핫한, 차가운 게시판의 좋아요 수에 대한 아웃박스 모두 저장
        UUID outboxId = UUID.randomUUID();
        postLikeOutboxRepository.save(PostLikeOutbox.newEvent(outboxId, postId, userId, delta));

        // 핫 게시판과 다르게 차가운 게시판시 바로 post 테이블에 즉시 업데이트
        if (delta != 0 && !postLikeRedisFacade.isHot(postId)) {
            postRepository.addLikeCount(postId, delta);
        }

        // 커밋 성공 시 퍼블리셔가 스트림에 메시지 발행
        // 커밋 실패하면 발행안되죠?
        // 레디스 장애시 메시지 발행 실패하면 outbox는
        // 처리되지 않으니 new로 계속 남아있음. 이는 추후에 재발행
        int finalDelta = delta;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (finalDelta != 0) {
                    postLikeMessagePublisher.publish(outboxId.toString(), postId, finalDelta);
                }
            }
        });
    }*/
}
