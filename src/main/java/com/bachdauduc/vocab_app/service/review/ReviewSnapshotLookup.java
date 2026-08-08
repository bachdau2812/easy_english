package com.bachdauduc.vocab_app.service.review;

import java.util.Map;

public record ReviewSnapshotLookup(
        Map<String, ReviewVocabSnapshot> hits,
        Map<String, Long> wordRevisions
) {
    public ReviewSnapshotLookup {
        hits = hits == null ? Map.of() : Map.copyOf(hits);
        wordRevisions = wordRevisions == null ? Map.of() : Map.copyOf(wordRevisions);
    }
}
