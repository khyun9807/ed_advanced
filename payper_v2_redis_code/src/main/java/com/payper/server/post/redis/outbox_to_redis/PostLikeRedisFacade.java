package com.payper.server.post.redis.outbox_to_redis;

import com.payper.server.post.entity.Post;
import com.payper.server.post.redis.PostLikeRedisKeys;
import com.payper.server.post.repository.PostLikeOutboxRepository;
import com.payper.server.post.repository.PostRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostLikeRedisFacade {
    // post:like:~ 를 조회하기 위함
    private final StringRedisTemplate stringRedisTemplate;
    private final PostLikeOutboxRepository postLikeOutboxRepository;
    private final PostRepository postRepository;

    public boolean isHot(long postId) {
        // sorted set안에 해당 포스트가 있는지 확인
        return stringRedisTemplate.opsForZSet().score(PostLikeRedisKeys.RANK, String.valueOf(postId)) != null;
    }

    // 아웃박스 중복 방지 세트에 있으면 이미 처리중|처리완료
    // 아웃박스 중복 방지 세트에 없으면 넣어서 중복처리 막기
    private boolean firstTime(String outboxId) {
        Boolean result = stringRedisTemplate
                .opsForValue()
                .setIfAbsent(
                        PostLikeRedisKeys.DEDUP_PREFIX + outboxId,
                        "1", // 밸류는 아무거나
                        Duration.ofMinutes(10) // TTL 설정
                        );

        return Boolean.TRUE.equals(result);
    }

    // 레디스에 반입반출 변화가 생길 때 잠깐 락걸기
    // 차가운 게시물(디비)->핫 게시물(레디스) 이동시 사용됨
    // 락을 못잡으면 지금 다른 워커가 해당 작업중이고, 다음 배치때 시도됨
    private boolean lockRank() {
        Boolean ok = stringRedisTemplate
                .opsForValue()
                .setIfAbsent(
                        PostLikeRedisKeys.RANK_LOCK,
                        "1", // 아무노래나 일단 틀어
                        java.time.Duration.ofSeconds(1) // TTL 설정
                        );
        return Boolean.TRUE.equals(ok);
    }

    private void unlockRank() {
        stringRedisTemplate.delete(PostLikeRedisKeys.RANK_LOCK);
    }

    // 핫 게시물일 때 호출. 레디스에서 좋아요수,랭킹,활동시각,더티 갱신
    // 좋아요 트랜잭션 후 아웃박스->레디스 반영하는 배치 워커에서 호출됨
    // 레디스 반영 & 디비 동기화 예약
    public void applyHotDelta(String outboxId, long postId, int delta, long now) {
        if (delta == 0) // 반영할게 없어
        return;

        if (!firstTime(outboxId)) // 이미 처리중|처리완료
        return;

        String postIdStr = String.valueOf(postId);

        // 카운트 갱신
        Long cntResult = stringRedisTemplate.opsForValue().increment(PostLikeRedisKeys.CNT_PREFIX + postIdStr, delta);
        if (cntResult == null) return;

        // 랭킹점수 갱신 -> 랭킹 변화
        stringRedisTemplate.opsForZSet().add(PostLikeRedisKeys.RANK, postIdStr, cntResult);

        // 최근 활동 시각 갱신. 다음 48시간까지는 active
        stringRedisTemplate.opsForZSet().add(PostLikeRedisKeys.ACTIVE, postIdStr, now);

        // redis->DB 동기화(1분배치)할 대상 기록
        stringRedisTemplate.opsForSet().add(PostLikeRedisKeys.DIRTY, postIdStr);
    }

    // 차가운 게시판이 디비에서 좋아요수 증가한 경우에 호출
    // 핫100에 들어올 자격있는지 확인. 있으면 랭크업
    // 좋아요 트랜잭션 후 아웃박스->레디스 반영하는 배치 워커에서 호출됨
    // 레디스 반영 & 디비 동기화 예약
    public void tryRankUp(String outboxId, long postId, long candidateCount, long now) {
        // 아웃박스 테이블에는 차가운 게시판에 대한 아웃박스도 저장되어있죠?
        // 해당 아웃박스에 대한 처리가 처리중|처리완료 인지 확인.
        if (!firstTime(outboxId)) return;

        // 반입반출 시작전 락
        if (!lockRank()) return; // 현재 락 못 얻으면 일단 리턴하여 다음 배치 노림

        try {
            String postIdStr = String.valueOf(postId);

            // 현재 세트의 요소 보유 수
            // 레디스의 핫100 세트의 요소 보유 수
            Long size = stringRedisTemplate.opsForZSet().size(PostLikeRedisKeys.RANK);
            if (size == null) size = 0L;

            // 반입여부 결정하기
            boolean promote;
            if (size < PostLikeRedisKeys.HOT_SIZE) {
                // 모자르면 그냥 반입
                promote = true;
            } else {
                // 현재 Hot100 최하위 점수 확인(0번째가 최소)
                var min = stringRedisTemplate.opsForZSet().rangeWithScores(PostLikeRedisKeys.RANK, 0, 0);
                double minScore = (min == null || min.isEmpty())
                        ? 0
                        : min.iterator().next().getScore();

                // 반입 평가 post의 라이크수가 더 크면 반입
                promote = candidateCount > minScore;
            }

            // 반입 반대면 리턴
            if (!promote) return;

            // 반입: 카운트 키 생성(Hot만 들고갈 것이므로 여기서 set)
            stringRedisTemplate.opsForValue().set(
                    PostLikeRedisKeys.CNT_PREFIX + postIdStr,
                    String.valueOf(candidateCount)
            );

            // 랭킹및활동 갱신
            stringRedisTemplate.opsForZSet().add(PostLikeRedisKeys.RANK, postIdStr, candidateCount);
            stringRedisTemplate.opsForZSet().add(PostLikeRedisKeys.ACTIVE, postIdStr, now);

            // 배치 동기화 대상 표시
            stringRedisTemplate.opsForSet().add(PostLikeRedisKeys.DIRTY, postIdStr);

            // 101개 이상이면 최하위 방출
            trimTo100();

        } finally {
            unlockRank(); // 락 프리
        }
    }

    // 핫 100을 초과한 최하위 게시물 정보 레디스에서 방출
    // 방출된 것은 1분 배치를 통해 디비에 동기화
    // 디비 동기화를 위한 flush에 방출대상 저장
    // 레디스 반영 & 디비 동기화 예약
    private void trimTo100() {
        Long size = stringRedisTemplate.opsForZSet().size(PostLikeRedisKeys.RANK);

        while (size != null && size > PostLikeRedisKeys.HOT_SIZE) {
            // 최하위 1개 찾기
            Set<String> evict = stringRedisTemplate.opsForZSet().range(PostLikeRedisKeys.RANK, 0, 0);
            if (evict == null || evict.isEmpty()) // 비어있다면 그만두기
                break;

            // 레디스에서 방출될 포스트 아이디
            String evictedId = evict.iterator().next();

            // 랭킹 및 활성 에서 제거
            stringRedisTemplate.opsForZSet().remove(PostLikeRedisKeys.RANK, evictedId);
            stringRedisTemplate.opsForZSet().remove(PostLikeRedisKeys.ACTIVE, evictedId);

            // DB로 최종 반영하기 위한 저장
            // 1분 배치가 처리하기 위한 저장
            stringRedisTemplate.opsForSet().add(PostLikeRedisKeys.FLUSH, evictedId);

            // 카운트 정보도 삭제
            stringRedisTemplate.delete(PostLikeRedisKeys.CNT_PREFIX + evictedId);

            // 처리 후 사이즈 갱신
            size = stringRedisTemplate.opsForZSet().size(PostLikeRedisKeys.RANK);
        }
    }

    @Transactional
    public int handleOutboxesLegacy(String workerId){
        // 처리 대상 300개 가져오기
        List<PostLikeOutbox> outboxes = postLikeOutboxRepository.findNewForProcessing(250);
        if (outboxes.isEmpty())
            return 0;

        // 처리 중 표시
        // 같은 트랜잭션 내에서 반영
        for (PostLikeOutbox outbox : outboxes) {
            outbox.markProcessing(workerId);
        }
        postLikeOutboxRepository.saveAll(outboxes);

        long now = System.currentTimeMillis();
        int processed=0;

        // 실제 처리하기
        for (PostLikeOutbox outbox : outboxes) {
            try {
                int delta=outbox.getDelta();
                if (delta == 0) {
                    outbox.markDone();
                    ++processed;
                    continue;
                }

                long postId = outbox.getPostId();
                String outboxId = outbox.getId();

                // outbox의 포스트가 핫하다면 == 레디스에 있다면
                if (isHot(postId)) {
                    applyHotDelta(outboxId, postId, outbox.getDelta(), now);
                } else { // outbox의 포스트가 차갑다면 == 레디스에 없다면
                    postRepository.addLikeCount(postId,delta);
                    Long likeCount = postRepository.getLikeCount(postId);
                    if (likeCount != null) {
                        tryRankUp(outboxId, postId, likeCount, now);
                    }
                }

                outbox.markDone();
                ++processed;
            } catch (Exception e) {
                // 에러시 다음 배치때 재시도
                // processing은 유지
                // processing이 오래된 outbox는 복구 배치 잡이 다시 new로 갱신하여 재시도
                log.error("outbox 처리 실패 id={}", outbox.getId(), e);
            }
        }

        postLikeOutboxRepository.saveAll(outboxes);

        return processed;
    }

    // 실제 처리(아웃박스 -> 레디스)
    // hot 게시판이면 redis 반영
    // cold 게시판이면 반입 반출 여부 가른 후 redis 반영
    // 메시지 구조 <키, 필드명, 필드값>
    @Transactional
    public void handleOneStreamMessage(MapRecord<String, Object, Object> message) {
        Map<Object, Object> messageBody = message.getValue();

        String outboxId = (String) messageBody.get("outboxId");
        long postId = (Long) messageBody.get("postId");
        int delta = (Integer) messageBody.get("delta");

        PostLikeOutbox postLikeOutbox =
                postLikeOutboxRepository.findById(outboxId).orElse(null);
        if (postLikeOutbox == null) return;

        if (postLikeOutbox.getStatus().equals("DONE")) return;

        long now = System.currentTimeMillis();

        if (isHot(postId)) {
            applyHotDelta(outboxId, postId, delta, now);
        } else {
            Long likeCount = postRepository.getLikeCount(postId);
            if (likeCount != null && likeCount > 0) tryRankUp(outboxId, postId, delta, now);
        }

        postLikeOutbox.markDone();
        postLikeOutboxRepository.save(postLikeOutbox);
    }

    @Transactional(readOnly = true)
    public void seedHot100FromDb() {
        stringRedisTemplate.delete(List.of(
                PostLikeRedisKeys.RANK, PostLikeRedisKeys.ACTIVE, PostLikeRedisKeys.DIRTY, PostLikeRedisKeys.FLUSH));

        long now = System.currentTimeMillis();

        List<Post> top100Posts = postRepository.findTop100ByLikeCount();
        if (top100Posts.isEmpty()) return;

        for (Post post : top100Posts) {
            long likeCount = post.getLikeCount();
            String postIdStr = post.getId().toString();

            stringRedisTemplate.opsForValue().set(PostLikeRedisKeys.CNT_PREFIX + postIdStr, String.valueOf(likeCount));

            stringRedisTemplate.opsForZSet().add(PostLikeRedisKeys.RANK, postIdStr, likeCount);

            stringRedisTemplate.opsForZSet().add(PostLikeRedisKeys.ACTIVE, postIdStr, now);
        }
    }
}
