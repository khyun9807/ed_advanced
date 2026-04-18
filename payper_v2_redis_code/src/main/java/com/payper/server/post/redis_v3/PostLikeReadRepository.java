package com.payper.server.post.redis_v3;

import com.payper.server.post.entity.PostLike;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeReadRepository extends JpaRepository<PostLike, Long> {

    @Query(value = "SELECT user_id FROM post_like WHERE post_id = :postId", nativeQuery = true)
    List<Long> findUserIdsByPostId(@Param("postId") long postId);
}
