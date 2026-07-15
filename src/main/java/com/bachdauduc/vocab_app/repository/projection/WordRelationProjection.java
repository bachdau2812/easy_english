package com.bachdauduc.vocab_app.repository.projection;

public interface WordRelationProjection {
    String getWordId();

    String getWord();

    String getPos();

    String getCertLevel();

    String getSynonyms();

    String getAntonyms();

    String getDerived();

    String getCoordinateTerms();

    String getFormOf();

    String getAltOf();
}
