package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.UserInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserInfoRepository extends JpaRepository<UserInfo, String> {
    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<UserInfo> findByUsername(String username);

    Optional<UserInfo> findByEmail(String email);
}
