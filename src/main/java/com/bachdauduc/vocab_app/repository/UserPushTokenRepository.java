package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.UserPushToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserPushTokenRepository extends JpaRepository<UserPushToken, String> {
    List<UserPushToken> findByUserId(String userId);
}
