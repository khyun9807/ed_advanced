package com.payper.server.post.post_like_legacy;

import com.payper.server.post.dto.PostResponse;
import com.payper.server.post.entity.Post;
import com.payper.server.post.repository.PostLikeRepository;
import com.payper.server.post.repository.PostRepository;
import com.payper.server.post.service.PostLikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostLikeLegacyService {
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;

    @Transactional
    public void doPostLikeLegacy(Long userId, Long postId) {
        // 일단 사용자-게시물 관계 넣기 시도
        int inserted = postLikeRepository.insertIgnore(postId, userId);

        // 좋아요 수 변화값
        int delta = 0;
        if (inserted > 0) {
            // 라이크
            delta += inserted;
        } else {
            // 언라이크
            // 실패시 이미 들어있기에 제거
            int deleted = postLikeRepository.deleteByPostIdAndUserId(postId, userId);
            if (deleted > 0) {
                delta -= deleted;
            }
        }

        if(delta !=0){
            postRepository.addLikeCount(postId,delta);
        }
    }

    @Transactional(readOnly = true)
    public List<PostResponse.PostList> getPostTop100Legacy(){
        List<Post> posts = postRepository.findTop100ByLikeCount();
        return posts.stream().map(PostResponse.PostList::from).toList();
    }
}
