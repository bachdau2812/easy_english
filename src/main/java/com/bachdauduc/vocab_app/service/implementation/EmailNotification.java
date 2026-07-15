package com.bachdauduc.vocab_app.service.implementation;

import com.bachdauduc.vocab_app.dto.request.NotificationRequest;
import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import com.bachdauduc.vocab_app.service.abstraction.SendNotification;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EmailNotification implements SendNotification {
    JavaMailSender javaMailSender;

    @Override
    public void send(NotificationRequest request) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(request.getRecipientId());
            helper.setSubject(request.getTitle());
            helper.setText(request.getBody(), true);

            javaMailSender.send(mimeMessage);
            log.info("Email notification sent: recipientId={}", request.getRecipientId());
        } catch (MailException exception) {
            log.error("Email notification failed: recipientId={}", request.getRecipientId(), exception);
            throw new AppException(ErrorCode.NOTIFICATION_SEND_FAILED);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }
}
