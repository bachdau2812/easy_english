package com.bachdauduc.vocab_app.controller;

import com.bachdauduc.vocab_app.dto.request.UpdateUserInfoRequest;
import com.bachdauduc.vocab_app.dto.response.ApiResponse;
import com.bachdauduc.vocab_app.dto.response.UserInfoResponse;
import com.bachdauduc.vocab_app.service.UserService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserController {
    UserService userService;

    @GetMapping("/info")
    public ApiResponse<UserInfoResponse> getUserInfo(@RequestParam String userId) {
        log.info("Request received: action=getUserInfo, userId={}", userId);
        return ApiResponse.<UserInfoResponse>builder()
                .message("Get user info successfully")
                .traceId(MDC.get("traceId"))
                .result(userService.getUserInfo(userId))
                .build();
    }

    @PutMapping("/info")
    public ApiResponse<UserInfoResponse> updateInfo(@Valid @RequestBody UpdateUserInfoRequest request) {
        log.info("Request received: action=updateInfo, userId={}", request.getUserId());
        return ApiResponse.<UserInfoResponse>builder()
                .message("User info updated")
                .traceId(MDC.get("traceId"))
                .result(userService.updateInfo(request))
                .build();
    }
}
