package com.bachdauduc.vocab_app.service.model;

public record WordExampleGenerationInput(
        String requestId,
        String wordId,
        String senseId,
        String word,
        String pos,
        String level,
        String englishSense,
        int requiredExampleCount
) {
}
