package com.bachdauduc.vocab_app.repository.projection;

public interface WordFormProjection {
    String getWordId();

    String getWord();

    String getPos();

    String getCertLevel();

    String getForm();

    String getTags();
}
