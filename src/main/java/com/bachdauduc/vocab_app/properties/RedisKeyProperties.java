package com.bachdauduc.vocab_app.properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@PropertySource("classpath:redis_keys.properties")
public class RedisKeyProperties {
    @Value("${redis.key.pre-register-info}")
    private String preRegisterInfoPattern;

    @Value("${redis.key.pre-register-code}")
    private String preRegisterCodePattern;

    @Value("${redis.key.forget-password-code}")
    private String forgetPasswordCodePattern;

    @Value("${redis.key.logout-token}")
    private String logoutTokenPattern;

    @Value("${redis.key.user-info}")
    private String userInfoPattern;

    @Value("${redis.key.word-with-trans}")
    private String wordWithTransPattern;

    @Value("${redis.key.word-without-trans}")
    private String wordWithoutTransPattern;

    @Value("${redis.key.current-review-wrong}")
    private String currentReviewWrongPattern;

    @Value("${redis.key.reading-quiz}")
    private String readingQuizPattern;

    @Value("${redis.key.review-vocab-revision}")
    private String reviewVocabRevisionPattern;

    @Value("${redis.key.review-vocab-snapshot}")
    private String reviewVocabSnapshotPattern;

    @Value("${redis.key.review-progress}")
    private String reviewProgressPattern;

    @Value("${redis.ttl.pre-register-minutes}")
    private long preRegisterTtlMinutes;

    @Value("${redis.ttl.forget-password-minutes}")
    private long forgetPasswordTtlMinutes;

    @Value("${redis.ttl.logout-days}")
    private long logoutTtlDays;

    @Value("${redis.ttl.current-review-wrong-hours}")
    private long currentReviewWrongTtlHours;

    @Value("${redis.ttl.review-vocab-snapshot-hours}")
    private long reviewVocabSnapshotTtlHours;

    @Value("${redis.ttl.review-vocab-snapshot-jitter-minutes}")
    private long reviewVocabSnapshotJitterMinutes;

    @Value("${redis.ttl.review-progress-hours}")
    private long reviewProgressTtlHours;

    @Value("${redis.ttl.review-progress-reservation-hours}")
    private long reviewProgressReservationTtlHours;

    public String preRegisterInfoKey(String email) {
        return preRegisterInfoPattern.formatted(email);
    }

    public String preRegisterCodeKey(String email) {
        return preRegisterCodePattern.formatted(email);
    }

    public String forgetPasswordCodeKey(String userId) {
        return forgetPasswordCodePattern.formatted(userId);
    }

    public String logoutTokenKey(String token) {
        return logoutTokenPattern.formatted(token);
    }

    public String userInfoKey(String userId) {
        return userInfoPattern.formatted(userId);
    }

    public String wordWithTransKey(String wordId) {
        return wordWithTransPattern.formatted(wordId);
    }

    public String wordWithoutTransKey(String wordId) {
        return wordWithoutTransPattern.formatted(wordId);
    }

    public String currentReviewWrongKey(String userVocabId) {
        return currentReviewWrongPattern.formatted(userVocabId);
    }

    public String reviewVocabRevisionKey(String wordId) {
        return reviewVocabRevisionPattern.formatted(wordId);
    }

    public String reviewVocabSnapshotKey(
            String wordId,
            String senseKey,
            String langCode,
            long revision
    ) {
        return reviewVocabSnapshotPattern.formatted(wordId, senseKey, langCode, revision);
    }

    public String reviewProgressKey(String userId, String wordId) {
        return reviewProgressPattern.formatted(userId, wordId);
    }

    public String readingQuizKey(String readingSourceId) {
        return readingQuizPattern.formatted(readingSourceId);
    }

    public Duration preRegisterTtl() {
        return Duration.ofMinutes(preRegisterTtlMinutes);
    }

    public Duration forgetPasswordTtl() {
        return Duration.ofMinutes(forgetPasswordTtlMinutes);
    }

    public Duration logoutTtl() {
        return Duration.ofDays(logoutTtlDays);
    }

    public Duration currentReviewWrongTtl() {
        return Duration.ofHours(currentReviewWrongTtlHours);
    }

    public Duration reviewVocabSnapshotTtl() {
        return Duration.ofHours(reviewVocabSnapshotTtlHours);
    }

    public Duration reviewVocabSnapshotJitter() {
        return Duration.ofMinutes(reviewVocabSnapshotJitterMinutes);
    }

    public Duration reviewProgressCleanupTtl() {
        return Duration.ofHours(reviewProgressTtlHours);
    }

    public Duration reviewProgressReservationTtl() {
        return Duration.ofHours(reviewProgressReservationTtlHours);
    }
}
