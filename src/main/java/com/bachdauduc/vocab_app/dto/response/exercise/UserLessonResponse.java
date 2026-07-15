package com.bachdauduc.vocab_app.dto.response.exercise;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserLessonResponse {
    String id;
    String userId;
    String lessonId;
    String lessonType;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
