package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.dto.request.UpdateUserInfoRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserService {
    UserInfoRepository userInfoRepository;
    RedisTemplate<String, String> redisTemplate;
    RedisKeyProperties redisKeyProperties;

    public UserInfoResponse getUserInfo(String userId) {
        log.debug("Start service: method=getUserInfo, userId={}", userId);
        String cacheKey = userInfoKey(userId);
        String cachedUserInfo = redisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cachedUserInfo)) {
            UserInfoResponse cachedResponse = RedisUtil.deserialize(cachedUserInfo, UserInfoResponse.class);
            if (cachedResponse != null) {
                log.info("User info loaded from redis: userId={}", userId);
                return cachedResponse;
            }
            log.warn("User info redis cache invalid: userId={}, key={}", userId, cacheKey);
        }

        UserInfo userInfo = userInfoRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        UserInfoResponse response = UserInfoResponse.from(userInfo);
        redisTemplate.opsForValue().set(cacheKey, RedisUtil.serialize(response));
        log.info("User info loaded from DB and cached: userId={}", userId);
        return response;
    }

    @Transactional
    public UserInfoResponse updateInfo(UpdateUserInfoRequest request) {
        log.debug("Start service: method=updateInfo, userId={}, requestedUsername={}",
                request.getUserId(), request.getUsername());
        UserInfo userInfo = userInfoRepository.findById(request.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        userInfoRepository.findByUsername(request.getUsername())
                .filter(existingUser -> !existingUser.getId().equals(request.getUserId()))
                .ifPresent(existingUser -> {
                    log.warn("Update user info failed: userId={}, requestedUsername={}, reason=username_exists",
                            request.getUserId(), request.getUsername());
                    throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
                });

        userInfo.setUsername(request.getUsername());
        UserInfo savedUser = userInfoRepository.save(userInfo);
        UserInfoResponse response = UserInfoResponse.from(savedUser);
        redisTemplate.opsForValue().set(userInfoKey(savedUser.getId()), RedisUtil.serialize(response));
        log.info("User info updated and cache refreshed: userId={}, username={}", savedUser.getId(), savedUser.getUsername());
        return response;
    }

    private String userInfoKey(String userId) {
        return redisKeyProperties.userInfoKey(userId);
    }
}
