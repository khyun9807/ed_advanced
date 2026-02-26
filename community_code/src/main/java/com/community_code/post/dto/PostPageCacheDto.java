package com.community_code.post.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.io.Serializable;
import java.util.List;

@Getter
@AllArgsConstructor
public class PostPageCacheDto implements Serializable {

    private final List<PostResponse.PostList> content;
    private final long totalElements;

    public static PostPageCacheDto from(Page<PostResponse.PostList> page) {
        return new PostPageCacheDto(page.getContent(), page.getTotalElements());
    }

    public Page<PostResponse.PostList> toPage(Pageable pageable) {
        return new PageImpl<>(content, pageable, totalElements);
    }
}