package com.bachdauduc.vocab_app.service.review;

import com.bachdauduc.vocab_app.dto.response.worddata.WordSenseResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordSoundResponse;
import com.bachdauduc.vocab_app.entity.UserVocabulary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewRequestContextTest {

    @Test
    void snapshotSerializationContainsOnlySharedDictionaryData() throws Exception {
        ReviewVocabSnapshot snapshot = snapshot("word-1", "sense:sense-1", "bank", "bờ sông", "audio-1");

        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(snapshot);

        assertThat(json)
                .contains("word-1", "sense:sense-1", "bank", "bờ sông")
                .doesNotContain("userId", "userVocabId", "level",
                        "currentLevelCorrectTurns", "nextReviewAt");
    }

    @Test
    void contextPreservesTargetOrderAndBuildsDistinctDistractorPoolsOnce() {
        UserVocabulary first = vocabulary("uv-1", "word-1");
        UserVocabulary second = vocabulary("uv-2", "word-2");
        UserVocabulary third = vocabulary("uv-3", "word-3");
        ReviewVocabSnapshot firstSnapshot =
                snapshot("word-1", "sense:sense-1", "bank", "bờ sông", "audio-1");
        ReviewVocabSnapshot secondSnapshot =
                snapshot("word-2", "sense:sense-2", "shore", "bờ sông", "audio-2");
        ReviewVocabSnapshot thirdSnapshot =
                snapshot("word-3", "sense:sense-3", "save", "tiết kiệm", "audio-2");

        ReviewRequestContext context = ReviewRequestContext.create(
                List.of(first, second, third),
                Map.of("uv-1", firstSnapshot, "uv-2", secondSnapshot, "uv-3", thirdSnapshot)
        );

        assertThat(context.userVocabIds()).containsExactly("uv-1", "uv-2", "uv-3");
        assertThat(context.meaningDistractors()).containsExactlyInAnyOrder("bờ sông", "tiết kiệm");
        assertThat(context.wordDistractors()).containsExactlyInAnyOrder("bank", "shore", "save");
        assertThat(context.soundDistractors()).containsExactlyInAnyOrder("audio-1", "audio-2");
        assertThat(context.snapshot("uv-2")).isSameAs(secondSnapshot);
    }

    private UserVocabulary vocabulary(String id, String wordId) {
        UserVocabulary vocabulary = new UserVocabulary();
        vocabulary.setId(id);
        vocabulary.setWordId(wordId);
        vocabulary.setSenseId("sense-" + id);
        vocabulary.setLevel(1);
        return vocabulary;
    }

    private ReviewVocabSnapshot snapshot(
            String wordId,
            String senseKey,
            String word,
            String meaning,
            String audioUrl
    ) {
        WordSenseResponse sense = WordSenseResponse.builder()
                .senseId(senseKey.substring(senseKey.indexOf(':') + 1))
                .wordId(wordId)
                .word(word)
                .shortMeaning(meaning)
                .build();
        WordSoundResponse sound = WordSoundResponse.builder()
                .wordId(wordId)
                .soundSource("MOCHI")
                .mp3Url(audioUrl)
                .build();
        return new ReviewVocabSnapshot(
                1,
                wordId,
                senseKey,
                "vi",
                word,
                "noun",
                meaning,
                sense,
                List.of(sound),
                List.of(new ReviewExample("example-" + wordId, word + " example", "bản dịch")),
                Instant.parse("2026-08-08T00:00:00Z")
        );
    }
}
