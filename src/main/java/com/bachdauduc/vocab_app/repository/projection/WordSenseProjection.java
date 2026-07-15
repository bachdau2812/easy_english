package com.bachdauduc.vocab_app.repository.projection;

public interface WordSenseProjection {
    String getSenseId();

    String getLocalizationId();

    String getWordId();

    String getWord();

    String getPos();

    String getCertLevel();

    String getShortMeaning();

    String getDefinition();

    String getSynonyms();

    String getAntonyms();

    String getTransLangCode();

    String getTransShortMeaning();

    String getTransDefinition();

    String getDerived();

    String getCoordinateTerms();

    String getFormOf();

    String getAltOf();
}
