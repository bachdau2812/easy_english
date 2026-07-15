package com.bachdauduc.vocab_app.service.abstraction;

import com.bachdauduc.vocab_app.dto.request.NotificationRequest;

public interface SendNotification {
    void send(NotificationRequest request);
}
