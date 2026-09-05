package com.bachdauduc.vocab_app.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    UNAUTHENTICATED(1001, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    USERNAME_ALREADY_EXISTS(2001, "Username already exists", HttpStatus.BAD_REQUEST),
    EMAIL_ALREADY_EXISTS(2002, "Email already exists", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(2003, "User not found", HttpStatus.NOT_FOUND),
    EMAIL_NOT_FOUND(2004, "Email not found", HttpStatus.NOT_FOUND),
    REGISTER_INFORMATION_EXPIRED(2005, "Register information expired", HttpStatus.BAD_REQUEST),
    INVALID_CODE(2006, "Invalid verification code", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(2007, "Invalid password", HttpStatus.BAD_REQUEST),
    INVALID_TOKEN(2008, "Invalid token", HttpStatus.UNAUTHORIZED),
    WORD_NOT_FOUND(2009, "Word not found", HttpStatus.NOT_FOUND),
    CATEGORY_NOT_FOUND(2010, "Category not found", HttpStatus.NOT_FOUND),
    WORD_EXAMPLE_NOT_FOUND(2011, "Word example not found", HttpStatus.NOT_FOUND),
    WORD_IDIOM_NOT_FOUND(2012, "Word idiom not found", HttpStatus.NOT_FOUND),
    TRANSLATION_FAILED(2013, "Translation failed", HttpStatus.BAD_REQUEST),
    USER_VOCABULARY_NOT_FOUND(2014, "User vocabulary not found", HttpStatus.NOT_FOUND),
    LISTEN_AND_TYPE_CHALLENGE_NOT_FOUND(2015, "Listen and type challenge not found", HttpStatus.NOT_FOUND),
    INVALID_EXERCISE_TYPE(2016, "Invalid exercise type", HttpStatus.BAD_REQUEST),
    INVALID_USER_VOCABULARY_REQUEST(2017, "Invalid user vocabulary request", HttpStatus.BAD_REQUEST),
    INVALID_REVIEW_VOCAB_TOTAL(2018, "Review vocab total must be 30, 60, or 90", HttpStatus.BAD_REQUEST),
    REVIEW_EXERCISE_EXHAUSTED(2019, "All vocab exercise types were generated for this review session", HttpStatus.BAD_REQUEST),
    WORD_SOUND_NOT_FOUND(2020, "Word sound not found", HttpStatus.NOT_FOUND),
    LESSON_NOT_FOUND(2021, "Lesson not found", HttpStatus.NOT_FOUND),
    INVALID_LESSON_TYPE(2022, "Invalid lesson type", HttpStatus.BAD_REQUEST),
    USER_VOCABULARY_ALREADY_EXISTS(2023, "User vocabulary already exists", HttpStatus.BAD_REQUEST),
    IELTS_READING_SOURCE_NOT_FOUND(2024, "IELTS reading source not found", HttpStatus.NOT_FOUND),
    IELTS_WRITING_EXERCISE_NOT_FOUND(2025, "IELTS writing exercise not found", HttpStatus.NOT_FOUND),
    INVALID_WRITING_REVIEW_REQUEST(2026, "Invalid writing review request", HttpStatus.BAD_REQUEST),
    WRITING_REVIEW_FAILED(2027, "Writing review failed", HttpStatus.BAD_REQUEST),
    GROQ_API_KEY_NOT_CONFIGURED(2028, "Groq API key is not configured", HttpStatus.BAD_REQUEST),
    WORD_EXAMPLE_GENERATION_FAILED(2029, "Word example generation failed", HttpStatus.BAD_REQUEST),
    INVALID_USER_VOCABULARY_INFO_TYPE(2030, "Invalid user vocabulary info type", HttpStatus.BAD_REQUEST),
    USER_VOCABULARY_EXPORT_FAILED(2031, "Could not export vocabulary", HttpStatus.INTERNAL_SERVER_ERROR),
    NOTIFICATION_TEMPLATE_NOT_FOUND(3001, "Notification template not found", HttpStatus.NOT_FOUND),
    UNSUPPORTED_NOTIFICATION_METHOD(3002, "Unsupported notification method", HttpStatus.BAD_REQUEST),
    PUSH_TOKEN_NOT_FOUND(3003, "Push token not found", HttpStatus.NOT_FOUND),
    NOTIFICATION_SEND_FAILED(3004, "Notification send failed", HttpStatus.BAD_REQUEST),

    ;
    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
