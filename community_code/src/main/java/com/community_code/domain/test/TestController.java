package com.community_code.domain.test;

import com.community_code.global.exception.ApiException;
import com.community_code.global.response.ApiResponse;
import com.community_code.global.response.ErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {
    @GetMapping("/ok")
    public ResponseEntity<ApiResponse<String>> test() {
        return ResponseEntity.ok(ApiResponse.ok("success"));
    }

    @GetMapping("/api")
    public ResponseEntity<ApiResponse<Void>> testFailure1() {
        throw new ApiException(ErrorCode.NOT_FOUND);
    }

    @GetMapping("/illegal")
    public ResponseEntity<ApiResponse<Void>> testFailure2() {
        throw new IllegalArgumentException();
    }

    @GetMapping("/exception")
    public ResponseEntity<ApiResponse<Void>> testFailure3() throws Exception {
        throw new Exception();
    }


}
