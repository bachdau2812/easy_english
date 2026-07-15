package com.bachdauduc.vocab_app.service.implementation;

import com.bachdauduc.vocab_app.dto.request.NotificationRequest;
import com.bachdauduc.vocab_app.entity.UserPushToken;
import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import com.bachdauduc.vocab_app.repository.UserPushTokenRepository;
import com.bachdauduc.vocab_app.service.abstraction.SendNotification;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PushNotification implements SendNotification {
    UserPushTokenRepository userPushTokenRepository;
    FirebaseApp firebaseApp;

    @Override
    public void send(NotificationRequest request) {
        List<UserPushToken> tokens = userPushTokenRepository.findByUserId(request.getRecipientId());
        if (tokens.isEmpty()) {
            throw new AppException(ErrorCode.PUSH_TOKEN_NOT_FOUND);
        }

        FirebaseMessaging firebaseMessaging = FirebaseMessaging.getInstance(firebaseApp);
        for (UserPushToken token : tokens) {
            sendToToken(firebaseMessaging, request, token.getPushToken());
        }

        log.info("Push notification sent: recipientId={}, tokenCount={}",
                request.getRecipientId(), tokens.size());
    }

    private void sendToToken(FirebaseMessaging firebaseMessaging, NotificationRequest request, String pushToken) {
        try {
            Message message = Message.builder()
                    .setToken(pushToken)
                    .setNotification(Notification.builder()
                            .setTitle(request.getTitle())
                            .setBody(request.getBody())
                            .build())
                    .build();
            firebaseMessaging.send(message);
        } catch (FirebaseMessagingException exception) {
            log.error("Push notification failed: recipientId={}", request.getRecipientId(), exception);
            throw new AppException(ErrorCode.NOTIFICATION_SEND_FAILED);
        }
    }
}
