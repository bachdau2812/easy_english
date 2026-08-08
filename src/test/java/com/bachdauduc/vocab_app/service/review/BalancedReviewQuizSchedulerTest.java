package com.bachdauduc.vocab_app.service.review;

import com.bachdauduc.vocab_app.constant.ExerciseType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.bachdauduc.vocab_app.constant.ExerciseType.VOCAB_LISTEN_AND_TYPE_WORD;
import static com.bachdauduc.vocab_app.constant.ExerciseType.VOCAB_MEANING_TO_SOUND;
import static com.bachdauduc.vocab_app.constant.ExerciseType.VOCAB_SENTENCE_BLANK_TO_SOUND;
import static com.bachdauduc.vocab_app.constant.ExerciseType.VOCAB_WORD_TO_MEANING;
import static org.assertj.core.api.Assertions.assertThat;

class BalancedReviewQuizSchedulerTest {

    private final BalancedReviewQuizScheduler scheduler =
            new BalancedReviewQuizScheduler(new Random(7));

    @Test
    void distributesThirtyTargetsAcrossAllTypesWithDifferenceAtMostOne() {
        Map<String, ExerciseType> assignments = scheduler.schedule(allEligibleTargets(30));

        assertThat(assignments).hasSize(30);
        assertThat(typeCounts(assignments).values())
                .hasSize(8)
                .allSatisfy(count -> assertThat(count).isBetween(3L, 4L));
    }

    @Test
    void assignsConstrainedTargetBeforeFlexibleTarget() {
        List<ReviewTargetEligibility> targets = List.of(
                target("flexible", VOCAB_LISTEN_AND_TYPE_WORD, VOCAB_WORD_TO_MEANING),
                target("sound-only", VOCAB_LISTEN_AND_TYPE_WORD)
        );

        Map<String, ExerciseType> assignments = scheduler.schedule(targets);

        assertThat(assignments)
                .containsEntry("sound-only", VOCAB_LISTEN_AND_TYPE_WORD)
                .containsEntry("flexible", VOCAB_WORD_TO_MEANING);
    }

    @Test
    void redistributesQuotaWhenSoundTypesAreUnavailable() {
        EnumSet<ExerciseType> withoutSound = EnumSet.allOf(ExerciseType.class);
        withoutSound.removeIf(type -> !type.isVocab());
        withoutSound.removeAll(Set.of(
                VOCAB_LISTEN_AND_TYPE_WORD,
                VOCAB_MEANING_TO_SOUND,
                VOCAB_SENTENCE_BLANK_TO_SOUND
        ));
        List<ReviewTargetEligibility> targets = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            targets.add(new ReviewTargetEligibility("uv-" + index, withoutSound));
        }

        Map<String, ExerciseType> assignments = scheduler.schedule(targets);
        Map<ExerciseType, Long> counts = typeCounts(assignments);

        assertThat(assignments).hasSize(16);
        assertThat(assignments).doesNotContainValue(VOCAB_LISTEN_AND_TYPE_WORD);
        assertThat(assignments).doesNotContainValue(VOCAB_MEANING_TO_SOUND);
        assertThat(assignments).doesNotContainValue(VOCAB_SENTENCE_BLANK_TO_SOUND);
        assertThat(counts.values()).allSatisfy(count -> assertThat(count).isBetween(3L, 4L));
    }

    @Test
    void omitsTargetWithNoEligibleType() {
        Map<String, ExerciseType> assignments = scheduler.schedule(List.of(
                new ReviewTargetEligibility("none", Set.of()),
                target("word", VOCAB_WORD_TO_MEANING)
        ));

        assertThat(assignments)
                .hasSize(1)
                .containsEntry("word", VOCAB_WORD_TO_MEANING)
                .doesNotContainKey("none");
    }

    private List<ReviewTargetEligibility> allEligibleTargets(int count) {
        EnumSet<ExerciseType> vocabTypes = EnumSet.allOf(ExerciseType.class);
        vocabTypes.removeIf(type -> !type.isVocab());
        List<ReviewTargetEligibility> targets = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            targets.add(new ReviewTargetEligibility("uv-" + index, vocabTypes));
        }
        return targets;
    }

    private ReviewTargetEligibility target(String id, ExerciseType... types) {
        return new ReviewTargetEligibility(id, EnumSet.copyOf(List.of(types)));
    }

    private Map<ExerciseType, Long> typeCounts(Map<String, ExerciseType> assignments) {
        Map<ExerciseType, Long> counts = new EnumMap<>(ExerciseType.class);
        assignments.values().forEach(type -> counts.merge(type, 1L, Long::sum));
        return counts;
    }
}
