package com.bachdauduc.vocab_app.service.review;

public record ReviewSnapshotIdentity(
        String userVocabId,
        String wordId,
        String senseKey,
        String langCode
) {
}
