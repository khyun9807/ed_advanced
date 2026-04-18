package com.payper.server.post.controller;

import com.payper.server.comment.dto.CommentRequest;
import com.payper.server.comment.dto.CommentResponse;
import com.payper.server.comment.service.CommentService;
import com.payper.server.global.response.ApiResponse;
import com.payper.server.post.dto.PostRequest;
import com.payper.server.post.dto.PostResponse;
import com.payper.server.post.dto.PostSortType;
import com.payper.server.post.entity.PostType;
import com.payper.server.post.cache.PostCacheService;
import com.payper.server.post.post_like_legacy.PostLikeLegacyService;
import com.payper.server.post.redis_fallback.PostLikeFacadeService;
import com.payper.server.post.redis_v3.PostLikeRedisServiceV3;
import com.payper.server.post.service.PostLikeService;
import com.payper.server.post.service.PostService;
import com.payper.server.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController implements PostApi {
    private final PostService postService;
    private final PostCacheService postCacheService;
    private final PostLikeService postLikeService;
    private final PostLikeFacadeService postLikeFacadeService;
    private final PostLikeRedisServiceV3 postLikeRedisServiceV3;
    private final CommentService commentService;
    private final PostLikeLegacyService postLikeLegacyService;

    /** 게시글 수정 */
    @PutMapping("/{postId}")
    public ResponseEntity<ApiResponse<Void>> updatePost(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long postId,
            @RequestBody @Valid PostRequest.UpdatePost request) {
        postService.updatePost(user.getId(), postId, request);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** 게시글 삭제 */
    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long postId) {
        postService.deletePost(user.getId(), postId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** 단일 게시글 조회 */
    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostResponse.PostDetail>> getPostDetail(@PathVariable Long postId) {
        PostResponse.PostDetail response = postCacheService.getPostDetail(postId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /** 단일 게시글 조회 */
    @GetMapping("/legacy/{postId}")
    public ResponseEntity<ApiResponse<PostResponse.PostDetail>> getPostDetailLegacy(@PathVariable Long postId) {
        PostResponse.PostDetail response = postCacheService.getPostDetailLegacy(postId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /** 게시글 리스트 조회 */
    @GetMapping()
    public ResponseEntity<ApiResponse<Page<PostResponse.PostList>>> getPosts(
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) PostType type,
            @RequestParam(defaultValue = "POSTING_DATE") PostSortType sort,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, sort.toSort(direction));
        Page<PostResponse.PostList> response = postService.getPosts(merchantId, type, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /** 댓글 작성 */
    @PostMapping("/{postId}/comments")
    public ResponseEntity<ApiResponse<Long>> createComment(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long postId,
            @RequestBody @Valid CommentRequest.CreateComment request) {
        Long commentId = commentService.createComment(user.getId(), postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(commentId));
    }

    /** 게시글 댓글 조회 */
    @GetMapping("/{postId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse.CommentList>> getPostComments(
            @PathVariable Long postId,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "20") int size) {
        CommentResponse.CommentList response = commentService.getPostComments(postId, cursorId, size);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /** 라이크, 언라이크 */
    @GetMapping("/{postId}/like")
    public ResponseEntity<ApiResponse<Void>> likePost(
            @PathVariable Long postId, @RequestParam Long userId // 임시. 인증을 피하기 위한
            // @AuthenticationPrincipal CustomUserDetails user
            ) {
        postLikeFacadeService.doPostLike(userId, postId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** 라이크, 언라이크 */
    @GetMapping("/{postId}/like/polling")
    public ResponseEntity<ApiResponse<Void>> likePostPolling(
            @PathVariable Long postId, @RequestParam Long userId // 임시. 인증을 피하기 위한
            // @AuthenticationPrincipal CustomUserDetails user
    ) {
        postLikeService.doPostLikePolling(userId, postId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/{postId}/like/legacy")
    public ResponseEntity<ApiResponse<Void>> likePostLegacy(
            @PathVariable Long postId, @RequestParam Long userId // 임시. 인증을 피하기 위한
            // @AuthenticationPrincipal CustomUserDetails user
    ) {
        postLikeLegacyService.doPostLikeLegacy(userId, postId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/top")
    public ResponseEntity<ApiResponse<List<String>>> getTopPosts(){
        List<String> postTop100 = postLikeFacadeService.getPostTop100();
        return ResponseEntity.ok(ApiResponse.ok(postTop100));
    }

    @GetMapping("/top/legacy")
    public ResponseEntity<ApiResponse<List<PostResponse.PostList>>> getTopPostsLegacy(){
        List<PostResponse.PostList> result = postLikeLegacyService.getPostTop100Legacy();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
