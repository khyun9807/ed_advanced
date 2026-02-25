package com.community_code.comment.controller;

import com.community_code.comment.dto.CommentRequest;
import com.community_code.comment.dto.CommentResponse;
import com.community_code.comment.CommentService;
import com.community_code.global.response.ApiResponse;
import com.community_code.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/comments")
@RequiredArgsConstructor
public class CommentController implements CommentApi {
    private final CommentService commentService;

    /**
     * 댓글 수정
     * 작성자만 수정 가능
     */
    @PutMapping("/{commentId}")
    public ResponseEntity<ApiResponse<Void>> updateComment(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long commentId,
            @RequestBody @Valid CommentRequest.UpdateComment request
    ) {
        commentService.updateComment(user.getId(), commentId, request);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 댓글 삭제
     * 작성자만 삭제 가능
     *
     * 자식 댓글은 삭제하지 않음
     */
    @DeleteMapping("/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long commentId) {

        commentService.deleteComment(user.getId(), commentId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * 내가 쓴 댓글 조회
     *
     * 무한 스크롤 방식
     *
     * 정렬: 최신 순
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CommentResponse.MyCommentList>> getMyComments(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "20") int size
    ) {
        CommentResponse.MyCommentList response = commentService.getMyComments(user.getId(), cursorId, size);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 자식 댓글 조회
     */
    @GetMapping("/{parentId}/replies")
    public ResponseEntity<ApiResponse<CommentResponse.CommentList>> getReplies(
            @PathVariable Long parentId,
            @RequestParam(required = false) Long cursorId,
            @RequestParam(defaultValue = "20") int size
    ) {
        CommentResponse.CommentList response = commentService.getReplies(parentId, cursorId, size);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
