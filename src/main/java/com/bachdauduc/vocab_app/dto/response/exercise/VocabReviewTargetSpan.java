package com.bachdauduc.vocab_app.dto.response.exercise;

/** Zero-based UTF-16 offsets [start, end) in example.sentence, before masking or HTML markup. */
public record VocabReviewTargetSpan(int start, int end, String text) {
}
