package com.payper.server.post.cache;

import com.payper.server.post.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostCacheRepository extends JpaRepository<Post, Long> {

    @Modifying
    @Query(value = "UPDATE posts SET view_count = :count WHERE id = :postId", nativeQuery = true)
    int setViewCount(@Param("postId") long postId, @Param("count") long count);
}
