package com.bachdauduc.vocab_app.constant;

public enum ExerciseType {
    VOCAB_WORD_TO_MEANING,
    VOCAB_FILL_MISSING_WORD_PART,
    VOCAB_LISTEN_AND_TYPE_WORD,
    VOCAB_CHOOSE_WORD_IN_SENTENCE_BLANK,
    VOCAB_FILL_WORD_IN_SENTENCE_BLANK,
    VOCAB_MEANING_TO_SOUND,
    VOCAB_SENTENCE_TO_MEANING,
    VOCAB_SENTENCE_BLANK_TO_SOUND,
    LAT_LISTEN_AND_TYPE;

    public boolean isVocab() {
        return name().startsWith("VOCAB_");
    }

    public boolean isListenAndType() {
        return name().startsWith("LAT_");
    }

    public boolean isQuiz() {
        return name().startsWith("QUIZ_");
    }
}
