package com.community_code.post.service;

import com.community_code.comment.CommentService;
import com.community_code.global.exception.ApiException;
import com.community_code.global.response.ErrorCode;
import com.community_code.merchant.entity.Merchant;
import com.community_code.merchant.repository.MerchantRepository;
import com.community_code.post.dto.PostRequest;
import com.community_code.post.dto.PostResponse;
import com.community_code.post.entity.Post;
import com.community_code.post.entity.PostType;
import com.community_code.post.repository.PostRepository;
import com.community_code.user.entity.User;
import com.community_code.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final MerchantRepository merchantRepository;
    private final CommentService commentService;
    private final PostCacheService postCacheService;

    /**
     * 게시글 작성
     */
    @Transactional
    public Long createPost(Long userId, Long merchantId, PostRequest.CreatePost request) {
        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        // 가맹점 조회
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ApiException(ErrorCode.MERCHANT_NOT_FOUND));

        // 게시글 생성
        Post post = Post.create(user, merchant, request.type(), request.title(), request.content());
        postRepository.save(post);

        log.info("게시글 생성 완료 - postId: {}, userId: {}, merchantId: {}", post.getId(), userId, merchantId);
        return post.getId();
    }

    /**
     * 게시글 수정
     */
    @Transactional
    public void updatePost(Long userId, Long postId, PostRequest.UpdatePost request) {
        // 게시글 조회 및 삭제 여부 체크
        Post post = postRepository.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));

        // 게시글 수정 권한 조회
        if(!post.isAuthor(userId)) {
            throw new ApiException(ErrorCode.NOT_POST_AUTHOR);
        }

        // 게시글 수정
        post.update(request.title(), request.content());

        log.info("게시글 수정 완료 - postId: {}", post.getId());
    }

    /**
     * 게시글 삭제
     */
    @Transactional
    public void deletePost(Long userId, Long postId) {
        // 게시글 조회
        Post post = postRepository.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));

        // 게시글 삭제 권한 조회
        if(!post.isAuthor(userId)) {
            throw new ApiException(ErrorCode.NOT_POST_AUTHOR);
        }

        // 게시글 삭제
        post.softDelete();
        log.info("게시글 삭제 완료 - postId: {}", post.getId());

        // 댓글 삭제 - 댓글 삭제에 실패해도 게시물 삭제는 진행되어야 하므로 try-catch로 묶음
        try {
            commentService.softDeleteByPostId(postId);
        } catch (Exception e) {
            log.error("댓글 삭제 실패 postId={}", postId);
        }
    }

    /**
     * 게시글 단일 조회
     */
    @Transactional(readOnly = true)
    public PostResponse.PostDetail getPostDetail(Long postId) {
        // 게시글 조회
        Post post = postRepository.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));

        return PostResponse.PostDetail.from(post);
    }

    /**
     * 게시글 리스트 조회
     */
    @Transactional(readOnly = true)
    public Page<PostResponse.PostList> getPostsLegacy(Long merchantId, PostType type, Pageable pageable) {

        Page<Post> posts;
        if(merchantId == null&&type == null) {
            posts = postRepository.findActivePosts(pageable);
        }
        else if(merchantId==null){
            posts = postRepository.findActivePostsByType(type, pageable);
        }
        else if(type==null){
            posts = postRepository.findActivePostsByMerchant(merchantId, pageable);
        }
        else{
            posts = postRepository.findActivePostsByMerchantAndType(merchantId, type, pageable);
        }


        log.info("post 조회 완료 {}", posts);
        return posts.map(PostResponse.PostList::from);
    }


    @Transactional(readOnly = true)
    public Page<PostResponse.PostList> getPosts(Long merchantId, PostType type, Pageable pageable) {
        return postCacheService.getPostsCached(merchantId, type, pageable).toPage(pageable);
    }


}