package com.bachdauduc.vocab_app.dto.request.exercise;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserLessonRequest {
    @NotBlank
    String userId;

    @NotBlank
    String lessonId;

    @NotBlank
    String lessonType;
}
