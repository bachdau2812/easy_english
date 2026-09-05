package com.bachdauduc.vocab_app.repository.projection;

public interface UserVocabularyExportProjection {
    String getWord();

    String getPos();

    String getWordSense();

    Integer getLevel();
}
