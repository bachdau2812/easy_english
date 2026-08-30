package com.bachdauduc.vocab_app.service.review;

import com.bachdauduc.vocab_app.constant.ExerciseType;
import com.bachdauduc.vocab_app.dto.response.worddata.WordSenseResponse;
import com.bachdauduc.vocab_app.entity.UserVocabulary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.bachdauduc.vocab_app.constant.ExerciseType.VOCAB_WORD_TO_MEANING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewAvailabilityServiceTest {
    @Mock ReviewVocabDataLoader reviewVocabDataLoader;
    @Mock ReviewProgressStore reviewProgressStore;

    @Test
    void excludesMissingIneligibleAndFullyReservedTargets() {
        UserVocabulary available = vocabulary("vocab-1", "word-1");
        UserVocabulary reserved = vocabulary("vocab-2", "word-2");
        UserVocabulary missing = vocabulary("vocab-3", "word-3");
        UserVocabulary ineligible = vocabulary("vocab-4", "word-4");
        List<UserVocabulary> due = List.of(available, reserved, missing, ineligible);
        when(reviewVocabDataLoader.load(due, "vi")).thenReturn(Map.of(
                available.getId(), snapshot("word-1", "bank", "ngan hang"),
                reserved.getId(), snapshot("word-2", "learn", "hoc"),
                ineligible.getId(), snapshot("word-4", "a", null)
        ));
        when(reviewProgressStore.availableTypes(eq("user-1"), eq("vocab-1"), anySet()))
                .thenReturn(Set.of(VOCAB_WORD_TO_MEANING));
        when(reviewProgressStore.availableTypes(eq("user-1"), eq("vocab-2"), anySet()))
                .thenReturn(Set.of());

        ReviewAvailabilityService service = new ReviewAvailabilityService(
                reviewVocabDataLoader,
                new ReviewQuizFactory(),
                reviewProgressStore
        );

        assertThat(service.findAvailable("user-1", due, "vi"))
                .extracting(UserVocabulary::getId)
                .containsExactly("vocab-1");
    }

    @Test
    void treatsSavedSensesOfTheSameWordAsIndependentTargets() {
        UserVocabulary firstSense = vocabulary("vocab-1", "shared-word");
        UserVocabulary secondSense = vocabulary("vocab-2", "shared-word");
        List<UserVocabulary> due = List.of(firstSense, secondSense);
        when(reviewVocabDataLoader.load(due, "vi")).thenReturn(Map.of(
                firstSense.getId(), snapshot("shared-word", "bank", "ngan hang"),
                secondSense.getId(), snapshot("shared-word", "bank", "bo song")
        ));
        when(reviewProgressStore.availableTypes(eq("user-1"), eq("vocab-1"), anySet()))
                .thenReturn(Set.of(VOCAB_WORD_TO_MEANING));
        when(reviewProgressStore.availableTypes(eq("user-1"), eq("vocab-2"), anySet()))
                .thenReturn(Set.of(VOCAB_WORD_TO_MEANING));

        ReviewAvailabilityService service = new ReviewAvailabilityService(
                reviewVocabDataLoader,
                new ReviewQuizFactory(),
                reviewProgressStore
        );

        assertThat(service.findAvailable("user-1", due, "vi"))
                .extracting(UserVocabulary::getId)
                .containsExactly("vocab-1", "vocab-2");
    }

    private UserVocabulary vocabulary(String id, String wordId) {
        UserVocabulary vocabulary = new UserVocabulary();
        vocabulary.setId(id);
        vocabulary.setUserId("user-1");
        vocabulary.setWordId(wordId);
        vocabulary.setSenseId("sense-" + id);
        vocabulary.setLevel(4);
        return vocabulary;
    }

    private ReviewVocabSnapshot snapshot(String wordId, String word, String meaning) {
        return new ReviewVocabSnapshot(
                ReviewVocabSnapshot.CURRENT_SCHEMA_VERSION,
                wordId,
                "sense:" + wordId,
                "vi",
                word,
                "noun",
                meaning,
                WordSenseResponse.builder().wordId(wordId).word(word).definition(meaning).build(),
                List.of(),
                List.of(),
                Instant.parse("2026-08-30T00:00:00Z")
        );
    }
}
