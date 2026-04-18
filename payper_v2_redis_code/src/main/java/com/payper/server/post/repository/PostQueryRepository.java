package com.payper.server.post.repository;

import static com.payper.server.post.entity.QPost.post;

import com.payper.server.post.entity.Post;
import com.payper.server.post.entity.PostType;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostQueryRepository {

    private final JPAQueryFactory queryFactory;

    public Page<Post> findActivePostsByCondition(Long merchantId, PostType type, Pageable pageable) {

        var query = queryFactory
                .selectFrom(post)
                .join(post.author).fetchJoin()
                .join(post.merchant).fetchJoin()
                .where(post.isDeleted.isFalse());

        // 동적 WHERE 조건: 값이 있을 때만 추가
        if (merchantId != null) {
            query.where(post.merchant.id.eq(merchantId));
        }
        if (type != null) {
            query.where(post.type.eq(type));
        }

        List<Post> content = query
                .orderBy(toOrderSpecifiers(pageable.getSort()))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = countByCondition(merchantId, type);

        return new PageImpl<>(content, pageable, total);
    }

    private Long countByCondition(Long merchantId, PostType type) {
        var query = queryFactory
                .select(post.count())
                .from(post)
                .where(post.isDeleted.isFalse());

        if (merchantId != null) {
            query.where(post.merchant.id.eq(merchantId));
        }
        if (type != null) {
            query.where(post.type.eq(type));
        }

        Long count = query.fetchOne();
        return count != null ? count : 0L;
    }

    private OrderSpecifier<?>[] toOrderSpecifiers(Sort sort) {
        return sort.stream()
                .map(order -> {
                    Order direction = order.isAscending() ? Order.ASC : Order.DESC;
                    return switch (order.getProperty()) {
                        case "likeCount" -> new OrderSpecifier<>(direction, post.likeCount);
                        case "viewCount" -> new OrderSpecifier<>(direction, post.viewCount);
                        case "commentCount" -> new OrderSpecifier<>(direction, post.commentCount);
                        default -> new OrderSpecifier<>(direction, post.createdAt);
                    };
                })
                .toArray(OrderSpecifier[]::new);
    }
}
