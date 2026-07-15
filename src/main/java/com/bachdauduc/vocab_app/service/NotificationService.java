package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.dto.request.NotificationRequest;
import com.bachdauduc.vocab_app.dto.request.SendNotificationRequest;
import com.bachdauduc.vocab_app.entity.NotificationTemplate;
import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import com.bachdauduc.vocab_app.repository.NotificationTemplateRepository;
import com.bachdauduc.vocab_app.service.implementation.EmailNotification;
import com.bachdauduc.vocab_app.service.implementation.PushNotification;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationService {
    NotificationTemplateRepository notificationTemplateRepository;
    EmailNotification emailNotification;
    PushNotification pushNotification;

    public String sendNotification(SendNotificationRequest request) {
        log.debug("Start service: method=sendNotification, recipientId={}, type={}, method={}",
                request.getRecipientId(), request.getNotificationType(), request.getNotificationMethod());

        NotificationTemplate template = notificationTemplateRepository
                .findByActionType(request.getNotificationType())
                .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_TEMPLATE_NOT_FOUND));

        String body = buildBody(template, request.getMetadata());
        NotificationRequest notificationRequest = NotificationRequest.builder()
                .recipientId(request.getRecipientId())
                .title(request.getTitle())
                .body(body)
                .build();

        String method = request.getNotificationMethod();
        if (!StringUtils.hasText(method)) {
            throw new AppException(ErrorCode.UNSUPPORTED_NOTIFICATION_METHOD);
        }

        switch (method.trim().toUpperCase()) {
            case "EMAIL" -> emailNotification.send(notificationRequest);
            case "PUSH" -> pushNotification.send(notificationRequest);
            default -> throw new AppException(ErrorCode.UNSUPPORTED_NOTIFICATION_METHOD);
        }

        log.info("Notification sent: recipientId={}, type={}, method={}",
                request.getRecipientId(), request.getNotificationType(), method);
        return "Gửi thông báo thành công";
    }

    public String buildBody(NotificationTemplate template, Map<String, String> metadata) {
        String body = template.getTemplate();
        if (metadata == null || metadata.isEmpty()) {
            return body;
        }

        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            body = body.replace(placeholder, entry.getValue() == null ? "" : entry.getValue());
        }
        return body;
    }
}
