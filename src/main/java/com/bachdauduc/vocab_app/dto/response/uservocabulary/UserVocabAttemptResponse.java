package com.bachdauduc.vocab_app.dto.response.uservocabulary;

import com.bachdauduc.vocab_app.constant.ExerciseType;
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
public class UserVocabAttemptResponse {
    String id;
    String attemptId;
    String userId;
    String userVocabId;
    ExerciseType exerciseType;
    String userAnswer;
    Boolean correct;
    Integer replayCount;
    LocalDateTime createdAt;
}
