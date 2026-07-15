package com.bachdauduc.vocab_app.repository.projection;

import java.time.LocalDateTime;

public interface UserVocabularyProjection {
    String getId();

    String getUserId();

    String getWordId();

    String getWord();

    String getSenseId();

    String getSenseLocalizedId();

    Integer getLevel();

    Integer getCurrentLevelCorrectTurns();

    LocalDateTime getNextReviewAt();

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();
}
