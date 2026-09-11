package com.bachdauduc.vocab_app.service.review;

import com.bachdauduc.vocab_app.constant.ExerciseType;
import com.bachdauduc.vocab_app.dto.response.exercise.VocabReviewQuizResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordSenseResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordSoundResponse;
import com.bachdauduc.vocab_app.entity.UserVocabulary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewQuizFactoryTest {
    private final ReviewQuizFactory factory = new ReviewQuizFactory(new Random(7));

    @ParameterizedTest
    @CsvSource(value = {
            "1, 3, 4, 3, 4", "2, 3, 4, 3, 4", "3, 3, 4, 3, 4",
            "4, 4, 4, 4, 7", "5, 4, 4, 4, 7", "6, 4, 4, 4, 7",
            "null, 3, 4, 3, 4"
    }, nullValues = "null")
    void fillExercisesUseHigherQuotasForLongWords(
            Integer level, int fiveMin, int fiveMax, int eightMin, int eightMax) {
        for (ExerciseType type : List.of(ExerciseType.VOCAB_FILL_WORD_IN_SENTENCE_BLANK,
                ExerciseType.VOCAB_FILL_MISSING_WORD_PART)) {
            assertMaskQuota("river", level, type, fiveMin, fiveMax);
            assertMaskQuota("elephant", level, type, eightMin, eightMax);
            assertMaskQuota("look over", level, type, eightMin, eightMax);
        }
    }

    @ParameterizedTest
    @CsvSource({"1, 1, 2", "2, 1, 2", "3, 1, 2", "4, 3, 3", "5, 3, 3", "6, 3, 3"})
    void preservesShortWordQuotas(int level, int fourMin, int fourMax) {
        for (ExerciseType type : List.of(ExerciseType.VOCAB_FILL_WORD_IN_SENTENCE_BLANK,
                ExerciseType.VOCAB_FILL_MISSING_WORD_PART)) {
            assertMaskQuota("cat", level, type, 1, 2);
            assertMaskQuota("book", level, type, fourMin, fourMax);
        }
    }

    private void assertMaskQuota(String word, Integer level, ExerciseType type, int minimum, int maximum) {
        UserVocabulary target = vocabulary(1);
        target.setLevel(level);
        ReviewVocabSnapshot snapshot = new ReviewVocabSnapshot(
                1, "word-1", "sense-1", "vi", word, "noun", "meaning",
                WordSenseResponse.builder().senseId("sense-1").build(), List.of(),
                List.of(new ReviewExample("example-1", "This " + word + " is beautiful.", "translation")),
                Instant.parse("2026-08-08T00:00:00Z"));
        ReviewRequestContext context = ReviewRequestContext.create(List.of(target), Map.of(target.getId(), snapshot));
        java.util.Set<Integer> observedCounts = new java.util.HashSet<>();
        for (int attempt = 0; attempt < 100; attempt++) {
            VocabReviewQuizResponse quiz = factory.create(target, snapshot, context, type);
            int count = quiz.getMetadata().size();
            assertThat(count).as("%s level %s: %s", type, level, word).isBetween(minimum, maximum);
            observedCounts.add(count);
            assertThat(quiz.getMaskedWord()).startsWith(word.substring(0, 1));
            StringBuilder restored = new StringBuilder(quiz.getMaskedWord());
            quiz.getMetadata().forEach((index, character) -> {
                assertThat(word.charAt(index)).isNotEqualTo(' ');
                assertThat(quiz.getMaskedWord().charAt(index)).isEqualTo('_');
                restored.setCharAt(index, character.charAt(0));
            });
            assertThat(restored.toString()).isEqualTo(word);
            if (type == ExerciseType.VOCAB_FILL_WORD_IN_SENTENCE_BLANK) {
                assertThat(quiz.getSentence()).isEqualTo("This " + quiz.getMaskedWord() + " is beautiful.");
            }
        }
        assertThat(observedCounts).contains(minimum, maximum);
    }

    @Test
    void createsEveryEligibleVocabQuizTypeFromOneRequestContext() {
        List<UserVocabulary> vocabularies = new ArrayList<>();
        Map<String, ReviewVocabSnapshot> snapshots = new LinkedHashMap<>();
        for (int index = 1; index <= 4; index++) {
            UserVocabulary vocabulary = vocabulary(index);
            vocabularies.add(vocabulary);
            snapshots.put(vocabulary.getId(), snapshot(index));
        }
        ReviewRequestContext context = ReviewRequestContext.create(vocabularies, snapshots);
        UserVocabulary target = vocabularies.getFirst();
        ReviewVocabSnapshot targetSnapshot = snapshots.get(target.getId());

        assertThat(factory.eligibleTypes(target, targetSnapshot, context))
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(ExerciseType.class).stream()
                        .filter(ExerciseType::isVocab)
                        .toList());

        for (ExerciseType type : factory.eligibleTypes(target, targetSnapshot, context)) {
            VocabReviewQuizResponse quiz = factory.create(target, targetSnapshot, context, type);
            assertThat(quiz.getExerciseType()).isEqualTo(type);
            assertThat(quiz.getUserVocabId()).isEqualTo("uv-1");
            assertThat(quiz.getWordId()).isEqualTo("word-1");
            assertThat(quiz.getCorrectAnswer()).isNotBlank();
            assertThat(quiz.getSense()).isSameAs(quiz.getWordSense());
        }
    }

    @Test
    void excludesDataDependentTypesWhenSoundAndExamplesAreMissing() {
        UserVocabulary target = vocabulary(1);
        ReviewVocabSnapshot sparse = new ReviewVocabSnapshot(
                1, "word-1", "sense-1", "vi", "planet", "noun", "hành tinh",
                WordSenseResponse.builder().senseId("sense-1").build(),
                List.of(), List.of(), Instant.parse("2026-08-08T00:00:00Z")
        );
        ReviewRequestContext context = ReviewRequestContext.create(
                List.of(target), Map.of(target.getId(), sparse));

        assertThat(factory.eligibleTypes(target, sparse, context))
                .containsExactlyInAnyOrder(
                        ExerciseType.VOCAB_WORD_TO_MEANING,
                        ExerciseType.VOCAB_FILL_MISSING_WORD_PART
                );
    }

    private UserVocabulary vocabulary(int index) {
        UserVocabulary vocabulary = new UserVocabulary();
        vocabulary.setId("uv-" + index);
        vocabulary.setUserId("user-1");
        vocabulary.setWordId("word-" + index);
        vocabulary.setSenseId("sense-" + index);
        vocabulary.setLevel(3);
        return vocabulary;
    }

    private ReviewVocabSnapshot snapshot(int index) {
        String word = List.of("planet", "forest", "river", "ocean").get(index - 1);
        String meaning = List.of("hành tinh", "rừng", "sông", "đại dương").get(index - 1);
        return new ReviewVocabSnapshot(
                1,
                "word-" + index,
                "sense-" + index,
                "vi",
                word,
                "noun",
                meaning,
                WordSenseResponse.builder().senseId("sense-" + index).wordId("word-" + index).build(),
                List.of(WordSoundResponse.builder()
                        .wordId("word-" + index)
                        .soundSource("MOCHI")
                        .mp3Url("https://audio/" + index + ".mp3")
                        .build()),
                List.of(new ReviewExample(
                        "example-" + index,
                        "This " + word + " is beautiful.",
                        "Ví dụ " + index
                )),
                Instant.parse("2026-08-08T00:00:00Z")
        );
    }
}
