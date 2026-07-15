package com.bachdauduc.vocab_app.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder

public class SendNotificationRequest {
    String recipientId;
    String title;
    String notificationMethod;
    String notificationType;
    Map<String, String> metadata;
}
