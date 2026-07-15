package com.bachdauduc.vocab_app.dto.request.uservocabulary;

import com.bachdauduc.vocab_app.constant.ExerciseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubmitReviewAttemptRequest {
    String attemptId;

    @NotBlank
    String userId;

    String userVocabId;

    @NotNull
    ExerciseType exerciseType;

    String userAnswer;

    @NotNull
    Boolean correct;

    Integer replayCount = 0;
}
