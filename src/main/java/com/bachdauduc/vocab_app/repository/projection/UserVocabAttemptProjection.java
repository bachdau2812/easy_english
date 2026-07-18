package com.bachdauduc.vocab_app.repository.projection;

import java.time.LocalDateTime;

public interface UserVocabAttemptProjection {
    String getId();

    String getAttemptId();

    String getUserId();

    String getUserVocabId();

    String getExerciseType();

    String getUserAnswer();

    String getReview();

    Boolean getCorrect();

    Integer getReplayCount();

    LocalDateTime getCreatedAt();
}
