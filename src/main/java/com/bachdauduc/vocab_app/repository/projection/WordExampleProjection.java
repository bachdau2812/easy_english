package com.bachdauduc.vocab_app.repository.projection;

public interface WordExampleProjection {
    String getWordExampleId();

    String getSenseId();

    String getWordSenseLocalizationId();

    String getWordId();

    String getWord();

    String getPos();

    String getCertLevel();

    String getSentence();

    String getTrans();
}
