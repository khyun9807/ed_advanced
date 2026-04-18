package com.payper.server.post.cache;

import com.payper.server.post.dto.PostResponse;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CacheEntry {
    private PostResponse.PostDetail data;
    private long logicalExpireAt;

    public boolean isStale() {
        return System.currentTimeMillis() > logicalExpireAt;
    }
}
