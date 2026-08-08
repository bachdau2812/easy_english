package com.bachdauduc.vocab_app.service.review;

import com.bachdauduc.vocab_app.constant.ExerciseType;
import com.bachdauduc.vocab_app.dto.response.exercise.VocabReviewQuizResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordSenseResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordSoundResponse;
import com.bachdauduc.vocab_app.entity.UserVocabulary;
import org.junit.jupiter.api.Test;

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
