package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.dto.request.RegisterUserRequest;
import com.bachdauduc.vocab_app.dto.request.ResetPasswordRequest;
import com.bachdauduc.vocab_app.dto.request.SendNotificationRequest;
import com.bachdauduc.vocab_app.dto.request.VerifyEmailRequest;
import com.bachdauduc.vocab_app.dto.request.LoginRequest;
import com.bachdauduc.vocab_app.dto.request.LogoutRequest;
import com.bachdauduc.vocab_app.dto.request.RefreshTokenRequest;
import com.bachdauduc.vocab_app.dto.response.AuthenticationResponse;
import com.bachdauduc.vocab_app.dto.response.UserInfoResponse;
import com.bachdauduc.vocab_app.entity.UserInfo;
import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import com.bachdauduc.vocab_app.properties.RedisKeyProperties;
import com.bachdauduc.vocab_app.repository.UserInfoRepository;
import com.bachdauduc.vocab_app.utils.RedisUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthService {
    private static final int VERIFICATION_CODE_LENGTH = 6;
    private static final int GENERATED_PASSWORD_LENGTH = 12;
    private static final String DEFAULT_USER_ROLE = "USER";

    UserInfoRepository userInfoRepository;
    RedisTemplate<String, String> redisTemplate;
    RedisKeyProperties redisKeyProperties;
    NotificationService notificationService;
    PasswordEncoder passwordEncoder;
    JwtService jwtService;

    public String registerUser(RegisterUserRequest request) {
        log.debug("Start service: method=registerUser, username={}, email={}",
                request.getUsername(), request.getEmail());
        String email = normalizeEmail(request.getEmail());
        validateUniqueUser(request.getUsername(), email);

        String code = RandomCode.generateRandomCode(VERIFICATION_CODE_LENGTH);
        RegisterUserRequest cachedRegisterRequest = RegisterUserRequest.builder()
                .username(request.getUsername())
                .password(request.getPassword())
                .email(email)
                .build();

        redisTemplate.opsForValue().set(
                redisKeyProperties.preRegisterInfoKey(email),
                RedisUtil.serialize(cachedRegisterRequest),
                redisKeyProperties.preRegisterTtl()
        );
        redisTemplate.opsForValue().set(
                redisKeyProperties.preRegisterCodeKey(email),
                code,
                redisKeyProperties.preRegisterTtl()
        );

        SendNotificationRequest notificationRequest = SendNotificationRequest.builder()
                .recipientId(email)
                .title("Xác thực người dùng")
                .notificationMethod("EMAIL")
                .notificationType("PRE_REGISTER")
                .metadata(Map.of(
                        "username", request.getUsername(),
                        "code", code
                ))
                .build();
        notificationService.sendNotification(notificationRequest);

        log.info("Pre-register information saved and verification notification sent: email={}", email);
        return "Gửi mã xác thực dùng thành công";
    }

    public AuthenticationResponse login(LoginRequest request) {
        log.debug("Start service: method=login, username={}", request.getUsername());
        UserInfo userInfo = userInfoRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getPassword(), userInfo.getPasswordHash())) {
            log.warn("Login failed: username={}, reason=invalid_password", request.getUsername());
            throw new AppException(ErrorCode.INVALID_PASSWORD);
        }

        String token = jwtService.generateToken(userInfo);
        log.info("User logged in: userId={}", userInfo.getId());
        return AuthenticationResponse.builder()
                .token(token)
                .userId(userInfo.getId())
                .username(userInfo.getUsername())
                .build();
    }

    public String logout(LogoutRequest request) {
        log.debug("Start service: method=logout, tokenLength={}",
                request.getToken() == null ? 0 : request.getToken().length());
        redisTemplate.opsForValue().set(
                redisKeyProperties.logoutTokenKey(request.getToken()),
                "logout",
                redisKeyProperties.logoutTtl()
        );

        log.info("User logged out token");
        return "Đăng xuất thành công";
    }

    public AuthenticationResponse refreshToken(RefreshTokenRequest request) {
        log.debug("Start service: method=refreshToken, tokenLength={}",
                request.getToken() == null ? 0 : request.getToken().length());
        try {
            String token = jwtService.refreshToken(request.getToken());
            log.info("Token refreshed successfully");
            return AuthenticationResponse.builder()
                    .token(token)
                    .build();
        } catch (JwtException exception) {
            log.warn("Token refresh failed: reason={}", exception.getMessage());
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }

    @Transactional
    public UserInfoResponse createUser(VerifyEmailRequest request) {
        String email = normalizeEmail(request.getEmail());
        log.debug("Start service: method=createUser, email={}, hasCode={}",
                email, StringUtils.hasText(request.getCode()));

        String infoKey = redisKeyProperties.preRegisterInfoKey(email);
        String codeKey = redisKeyProperties.preRegisterCodeKey(email);
        String cachedRegisterInfo = redisTemplate.opsForValue().get(infoKey);
        String cachedCode = redisTemplate.opsForValue().get(codeKey);

        if (!StringUtils.hasText(cachedRegisterInfo) || !StringUtils.hasText(cachedCode)) {
            log.warn("Create user failed: email={}, reason=expired_register_information", email);
            throw new AppException(ErrorCode.REGISTER_INFORMATION_EXPIRED);
        }
        if (!cachedCode.equals(request.getCode())) {
            log.warn("Create user failed: email={}, reason=invalid_code", email);
            throw new AppException(ErrorCode.INVALID_CODE);
        }

        RegisterUserRequest registerRequest = RedisUtil.deserialize(cachedRegisterInfo, RegisterUserRequest.class);
        if (registerRequest == null) {
            log.warn("Create user failed: email={}, reason=invalid_cached_register_info", email);
            throw new AppException(ErrorCode.REGISTER_INFORMATION_EXPIRED);
        }
        if (!email.equalsIgnoreCase(registerRequest.getEmail())) {
            log.warn("Create user failed: email={}, cachedEmail={}, reason=email_mismatch",
                    email, registerRequest.getEmail());
            throw new AppException(ErrorCode.REGISTER_INFORMATION_EXPIRED);
        }

        validateUniqueUser(registerRequest.getUsername(), registerRequest.getEmail());

        UserInfo userInfo = new UserInfo();
        userInfo.setId(UUID.randomUUID().toString());
        userInfo.setUsername(registerRequest.getUsername());
        userInfo.setEmail(registerRequest.getEmail());
        userInfo.setPasswordHash(passwordEncoder.encode(registerRequest.getPassword()));
        userInfo.setUserRole(registerRequest.getUserRole() == null ? DEFAULT_USER_ROLE : registerRequest.getUserRole());

        UserInfo savedUser = userInfoRepository.save(userInfo);
        redisTemplate.delete(infoKey);
        redisTemplate.delete(codeKey);

        SendNotificationRequest notificationRequest = SendNotificationRequest.builder()
                .recipientId(savedUser.getEmail())
                .title("Chào mừng đến với ứng dụng học từ vựng BACHDEPZAI")
                .notificationMethod("EMAIL")
                .notificationType("WELCOME_USER")
                .metadata(Map.of("username", savedUser.getUsername()))
                .build();
        notificationService.sendNotification(notificationRequest);

        log.info("User created: userId={}", savedUser.getId());
        return UserInfoResponse.from(savedUser);
    }

    @Transactional
    public String resetPassword(ResetPasswordRequest request) {
        log.debug("Start service: method=resetPassword, userId={}", request.getUserId());
        UserInfo userInfo = userInfoRepository.findById(request.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getOldPassword(), userInfo.getPasswordHash())) {
            log.warn("Reset password failed: userId={}, reason=invalid_old_password", request.getUserId());
            throw new AppException(ErrorCode.INVALID_PASSWORD);
        }

        userInfo.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userInfoRepository.save(userInfo);
        log.info("Password reset: userId={}", userInfo.getId());
        return "Đổi mật khẩu thành công";
    }

    public String forgetPasswordRequest(String email) {
        log.debug("Start service: method=forgetPasswordRequest, email={}", email);
        UserInfo userInfo = userInfoRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.EMAIL_NOT_FOUND));

        String code = RandomCode.generateRandomCode(VERIFICATION_CODE_LENGTH);
        redisTemplate.opsForValue().set(
                redisKeyProperties.forgetPasswordCodeKey(userInfo.getId()),
                code,
                redisKeyProperties.forgetPasswordTtl()
        );

        SendNotificationRequest notificationRequest = SendNotificationRequest.builder()
                .recipientId(userInfo.getEmail())
                .title("Đặt lại mật khẩu")
                .notificationMethod("EMAIL")
                .notificationType("FORGET_PASSWORD")
                .metadata(Map.of(
                        "username", userInfo.getUsername(),
                        "code", code
                ))
                .build();
        notificationService.sendNotification(notificationRequest);

        log.info("Forget password requested: userId={}", userInfo.getId());
        return "Gửi yêu cầu thành công";
    }

    @Transactional
    public String submitCodeForgetPasswordRequest(VerifyEmailRequest request) {
        log.debug("Start service: method=submitCodeForgetPasswordRequest, email={}, hasCode={}",
                request.getEmail(), StringUtils.hasText(request.getCode()));
        if (!StringUtils.hasText(request.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_NOT_FOUND);
        }

        UserInfo userInfo = userInfoRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.EMAIL_NOT_FOUND));

        String codeKey = redisKeyProperties.forgetPasswordCodeKey(userInfo.getId());
        String cachedCode = redisTemplate.opsForValue().get(codeKey);
        if (!StringUtils.hasText(cachedCode)) {
            log.warn("Forget password submit failed: userId={}, reason=expired_code", userInfo.getId());
            throw new AppException(ErrorCode.REGISTER_INFORMATION_EXPIRED);
        }
        if (!cachedCode.equals(request.getCode())) {
            log.warn("Forget password submit failed: userId={}, reason=invalid_code", userInfo.getId());
            throw new AppException(ErrorCode.INVALID_CODE);
        }

        String newPassword = RandomCode.generateRandomPassword(GENERATED_PASSWORD_LENGTH);
        userInfo.setPasswordHash(passwordEncoder.encode(newPassword));
        userInfoRepository.save(userInfo);
        redisTemplate.delete(codeKey);

        SendNotificationRequest notificationRequest = SendNotificationRequest.builder()
                .recipientId(userInfo.getEmail())
                .title("Mật khẩu mới cho " + userInfo.getUsername())
                .notificationMethod("EMAIL")
                .notificationType("NEW_PASSWORD")
                .metadata(Map.of(
                        "username", userInfo.getUsername(),
                        "newPassword", newPassword
                ))
                .build();
        notificationService.sendNotification(notificationRequest);

        log.info("Forget password completed: userId={}", userInfo.getId());
        return "Gửi mật khẩu mới thành công";
    }

    private void validateUniqueUser(String username, String email) {
        log.debug("Validate unique user: username={}, email={}", username, email);
        if (userInfoRepository.existsByUsername(username)) {
            log.warn("Unique user validation failed: username={}, reason=username_exists", username);
            throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        if (userInfoRepository.existsByEmail(email)) {
            log.warn("Unique user validation failed: email={}, reason=email_exists", email);
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new AppException(ErrorCode.EMAIL_NOT_FOUND);
        }
        return email.trim().toLowerCase();
    }
}
