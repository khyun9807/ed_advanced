package com.payper.server.post.repository;

import com.payper.server.post.entity.Post;
import com.payper.server.post.entity.PostType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    Optional<Post> findByIdAndIsDeletedFalse(Long id);

    boolean existsByIdAndIsDeletedFalse(Long id);

    @Modifying
    @Query("""
        update Post p
        set p.commentCount = case
            when p.commentCount < :count then 0
            else p.commentCount - :count
        end
        where p.id = :postId
    """)
    void decreaseCommentCount(@Param("postId") Long postId, @Param("count") long count);

    @Query(value = """
              select p from Post p
              join fetch p.author
              join fetch p.merchant
              where p.isDeleted = false
                and (:merchantId IS NULL or p.merchant.id = :merchantId)
                and (:type IS NULL or p.type = :type)
            """, countQuery = """
              select count(p) from Post p
              where p.isDeleted = false
                and (:merchantId IS NULL or p.merchant.id = :merchantId)
                and (:type IS NULL or p.type = :type)
            """)
    Page<Post> findActivePostsByCondition(
            @Param("merchantId") Long merchantId, @Param("type") PostType type, Pageable pageable);

    // posts.like_count는 "cold일 때만" 즉시 갱신
    @Modifying
    @Query(value = """
      UPDATE posts
      SET like_count = like_count + :delta
      WHERE id = :postId
        AND (like_count + :delta) >= 0
      """, nativeQuery = true)
    int addLikeCount(@Param("postId") long postId, @Param("delta") int delta);

    @Query(value = "SELECT like_count FROM posts WHERE id = :postId", nativeQuery = true)
    Long getLikeCount(@Param("postId") long postId);

    @Modifying
    @Query(value = "UPDATE posts SET like_count = :cnt WHERE id = :postId", nativeQuery = true)
    int setLikeCount(@Param("postId") long postId, @Param("cnt") long cnt);

    @Query(value = "select p from Post p join fetch p.merchant join fetch p.author order by p.likeCount desc limit 100")
    List<Post> findTop100ByLikeCount();

    @Modifying
    @Query("update Post p set p.viewCount = p.viewCount + 1 where p.id = :postId")
    void incrementViewCount(@Param("postId") Long postId);
}
