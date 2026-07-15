package com.bachdauduc.vocab_app.repository.projection;

public interface UserVocabStatisticProjection {
    Long getTotalAttempts();

    Long getCorrectQuizAttempt();

    Long getWrongQuizAttempt();

    Long getTotalUniqueVocab();

    Long getCorrectUniqueVocab();

    Long getWrongUniqueVocab();

    Long getWrongCountVocab();
}
