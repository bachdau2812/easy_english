package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Integer> {
    Optional<NotificationTemplate> findByActionType(String actionType);
}
