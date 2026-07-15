package com.bachdauduc.vocab_app.controller;

import com.bachdauduc.vocab_app.dto.request.ForgetPasswordRequest;
import com.bachdauduc.vocab_app.dto.request.LoginRequest;
import com.bachdauduc.vocab_app.dto.request.LogoutRequest;
import com.bachdauduc.vocab_app.dto.request.RefreshTokenRequest;
import com.bachdauduc.vocab_app.dto.request.RegisterUserRequest;
import com.bachdauduc.vocab_app.dto.request.ResetPasswordRequest;
import com.bachdauduc.vocab_app.dto.request.VerifyEmailRequest;
import com.bachdauduc.vocab_app.dto.response.ApiResponse;
import com.bachdauduc.vocab_app.dto.response.AuthenticationResponse;
import com.bachdauduc.vocab_app.dto.response.UserInfoResponse;
import com.bachdauduc.vocab_app.service.AuthService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthController {
    AuthService authService;

    @PostMapping("/register")
    public ApiResponse<String> registerUser(@Valid @RequestBody RegisterUserRequest request) {
        log.info("Request received: action=registerUser, username={}", request.getUsername());
        return success(authService.registerUser(request), "Register request created");
    }

    @PostMapping("/login")
    public ApiResponse<AuthenticationResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Request received: action=login, username={}", request.getUsername());
        return success(authService.login(request), "Login successfully");
    }

    @PostMapping("/logout")
    public ApiResponse<String> logout(@Valid @RequestBody LogoutRequest request) {
        log.info("Request received: action=logout");
        return success(authService.logout(request), "Logout successfully");
    }

    @PostMapping("/refresh-token")
    public ApiResponse<AuthenticationResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("Request received: action=refreshToken");
        return success(authService.refreshToken(request), "Refresh token successfully");
    }

    @PostMapping("/verify-email")
    public ApiResponse<UserInfoResponse> createUser(@Valid @RequestBody VerifyEmailRequest request) {
        log.info("Request received: action=createUser, email={}", request.getEmail());
        return success(authService.createUser(request), "User created");
    }

    @PostMapping("/reset-password")
    public ApiResponse<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        log.info("Request received: action=resetPassword, userId={}", request.getUserId());
        return success(authService.resetPassword(request), "Password reset");
    }

    @PostMapping("/forgot-password")
    public ApiResponse<String> forgetPasswordRequest(@Valid @RequestBody ForgetPasswordRequest request) {
        log.info("Request received: action=forgetPasswordRequest, email={}", request.getEmail());
        return success(authService.forgetPasswordRequest(request.getEmail()), "Forget password request created");
    }

    @PostMapping("/forgot-password/submit-code")
    public ApiResponse<String> submitCodeForgetPasswordRequest(@Valid @RequestBody VerifyEmailRequest request) {
        log.info("Request received: action=submitCodeForgetPasswordRequest, email={}", request.getEmail());
        return success(authService.submitCodeForgetPasswordRequest(request), "New password sent");
    }

    private <T> ApiResponse<T> success(T result, String message) {
        return ApiResponse.<T>builder()
                .message(message)
                .traceId(MDC.get("traceId"))
                .result(result)
                .build();
    }
}
