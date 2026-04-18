package com.payper.server.post.repository;

import com.payper.server.post.redis.outbox_to_redis.PostLikeOutbox;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeOutboxRepository extends JpaRepository<PostLikeOutbox, Long> {
    // 처리 대상 가져오기를 안전하게 하기 위해
    // FOR UPDATE SKIP LOCKED 를 쓰기 위해 네이티브 사용
    @Query(value = """
      SELECT * FROM post_like_outbox
      WHERE status = 'NEW'
      ORDER BY created_at
      LIMIT :limit
      FOR UPDATE SKIP LOCKED
      """, nativeQuery = true)
    List<PostLikeOutbox> findNewForProcessing(@Param("limit") int limit);

    // 오래 PROCESSING인 것을 다시 NEW로 되돌리는 복구용
    @Modifying
    @Query(value = """
      UPDATE post_like_outbox
      SET status='NEW', locked_at=NULL, locked_by=NULL
      WHERE status='PROCESSING' AND locked_at < (NOW(6) - INTERVAL :seconds SECOND)
      """, nativeQuery = true)
    int recoverStuck(@Param("seconds") int seconds);

    @Modifying
    @Query(value = """
      UPDATE post_like_outbox
      SET status='DONE', processed_at=NOW(6)
      WHERE id IN (:ids)
      """, nativeQuery = true)
    int markDoneByIds(@Param("ids") List<String> ids);

    Optional<PostLikeOutbox> findById(String id);

    List<PostLikeOutbox> findTop200ByStatusOrderByCreatedAtAsc(String status);
}
