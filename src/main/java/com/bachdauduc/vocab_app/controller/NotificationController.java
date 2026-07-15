package com.bachdauduc.vocab_app.controller;

import com.bachdauduc.vocab_app.dto.request.SendNotificationRequest;
import com.bachdauduc.vocab_app.dto.response.ApiResponse;
import com.bachdauduc.vocab_app.service.NotificationService;
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
@RequestMapping("/notifications")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationController {
    NotificationService notificationService;

    @PostMapping("/send")
    public ApiResponse<String> sendNotification(@RequestBody SendNotificationRequest request) {
        log.info("Request received: action=sendNotification, recipientId={}, type={}, method={}",
                request.getRecipientId(), request.getNotificationType(), request.getNotificationMethod());
        return ApiResponse.<String>builder()
                .message("Notification sent")
                .traceId(MDC.get("traceId"))
                .result(notificationService.sendNotification(request))
                .build();
    }
}
