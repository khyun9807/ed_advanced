package com.community_code.post.service;

import com.community_code.post.dto.PostPageCacheDto;
import com.community_code.post.dto.PostResponse;
import com.community_code.post.entity.Post;
import com.community_code.post.entity.PostType;
import com.community_code.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PostCacheService {

    private final PostRepository postRepository;

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = "getPosts",
            key = "'posts:m:' + (#merchantId == null ? 'all' : #merchantId) +"
                    + "':t:' + (#type == null ? 'all' : #type.name()) +"
                    + "':o:' + #pageable.offset +"
                    + "':s:' + #pageable.pageSize +"
                    + "':sort:' + #pageable.sort.toString()",
            cacheManager = "postsCacheManager"
    )
    public PostPageCacheDto getPostsCached(Long merchantId, PostType type, Pageable pageable) {

        Page<Long> idPage;
        if (merchantId == null && type == null) {
            idPage = postRepository.findActivePostIds(pageable);
        } else if (merchantId == null) {
            idPage = postRepository.findActivePostIdsByType(type, pageable);
        } else if (type == null) {
            idPage = postRepository.findActivePostIdsByMerchant(merchantId, pageable);
        } else {
            idPage = postRepository.findActivePostIdsByMerchantAndType(merchantId, type, pageable);
        }

        Page<Post> posts = assemblePage(idPage, pageable);
        Page<PostResponse.PostList> dtoPage = posts.map(PostResponse.PostList::from);

        return PostPageCacheDto.from(dtoPage);
    }

    private Page<Post> assemblePage(Page<Long> idPage, Pageable pageable) {
        List<Long> ids = idPage.getContent();
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, idPage.getTotalElements());
        }

        List<Post> posts = postRepository.fetchWithAuthorAndMerchantByIds(ids);

        // ✅ idPage의 순서대로 정렬 복원
        Map<Long, Integer> order = new HashMap<>();

        for (int i = 0; i < ids.size(); i++) order.put(ids.get(i), i);
        posts.sort(Comparator.comparingInt(p -> order.get(p.getId())));

        return new PageImpl<>(posts, pageable, idPage.getTotalElements());
    }
}
