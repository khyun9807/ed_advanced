package com.payper.server.post.repository;

import com.payper.server.post.entity.PostLike;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    @Query(value = "select p from PostLike p where p.user.id=:userId and p.post.id=:postId")
    Optional<PostLike> findByUserAndPost(Long userId, Long postId);

    // 유니크키 덕분에 "처음 누르면 1, 이미 눌렀으면 0"
    @Modifying
    @Query(value = "INSERT IGNORE INTO post_like(post_id, user_id) VALUES (:postId, :userId)", nativeQuery = true)
    int insertIgnore(@Param("postId") long postId, @Param("userId") long userId);

    // unlike 판단시 지우기
    @Modifying
    @Query(value = "DELETE FROM post_like WHERE post_id = :postId AND user_id = :userId", nativeQuery = true)
    int deleteByPostIdAndUserId(@Param("postId") long postId, @Param("userId") long userId);
}
