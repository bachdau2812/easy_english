package com.bachdauduc.vocab_app.dto.response;

import com.bachdauduc.vocab_app.entity.UserInfo;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserInfoResponse {
    String id;
    String username;
    String email;
    String userRole;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    public static UserInfoResponse from(UserInfo userInfo) {
        return UserInfoResponse.builder()
                .id(userInfo.getId())
                .username(userInfo.getUsername())
                .email(userInfo.getEmail())
                .userRole(userInfo.getUserRole())
                .createdAt(userInfo.getCreatedAt())
                .updatedAt(userInfo.getUpdatedAt())
                .build();
    }
}
