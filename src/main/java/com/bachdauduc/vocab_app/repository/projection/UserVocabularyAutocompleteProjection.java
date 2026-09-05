package com.bachdauduc.vocab_app.repository.projection;

public interface UserVocabularyAutocompleteProjection {
    String getUserVocabId();

    String getWord();

    Integer getLevel();

    String getPos();
}
