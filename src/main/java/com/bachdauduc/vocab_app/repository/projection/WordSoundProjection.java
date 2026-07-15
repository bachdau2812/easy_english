package com.bachdauduc.vocab_app.repository.projection;

public interface WordSoundProjection {
    String getWordId();

    String getIpa();

    String getTags();

    String getSoundSource();

    String getOggUrl();

    String getMp3Url();

    String getEnpr();
}
