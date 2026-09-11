package com.bachdauduc.vocab_app.service.review;

import com.bachdauduc.vocab_app.constant.ExerciseType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(ints = {30, 60, 90})
    void balancesEachSessionQuota(int quota) {
        var counts = typeCounts(scheduler.schedule(allEligibleTargets(quota)));
        assertThat(counts).hasSize(8);
        assertThat(counts.values()).allSatisfy(count -> assertThat(count).isBetween((long) quota / 8, (long) (quota + 7) / 8));
    }

    @Test
    void reassignsEarlierChoicesWhenConstraintsStillPermitPerfectBalance() {
        List<ExerciseType> types = EnumSet.allOf(ExerciseType.class).stream().filter(ExerciseType::isVocab).toList();
        List<ReviewTargetEligibility> targets = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            targets.add(target("uv-" + i, types.get(i), types.get((i + 1) % types.size())));
        }
        for (int seed = 0; seed < 40; seed++) {
            var assignments = new BalancedReviewQuizScheduler(new Random(seed)).schedule(targets);
            assertThat(typeCounts(assignments)).as("seed %s", seed).hasSize(8);
            assertThat(typeCounts(assignments).values()).containsOnly(1L);
        }
    }

    @Test
    void pendingAssignmentsCompensateForAlreadyEmittedQuizzes() {
        var emitted = Map.of(VOCAB_WORD_TO_MEANING, 4);
        var counts = typeCounts(scheduler.schedule(allEligibleTargets(26), emitted));
        counts.merge(VOCAB_WORD_TO_MEANING, 4L, Long::sum);
        assertThat(counts).hasSize(8);
        assertThat(counts.values()).allSatisfy(count -> assertThat(count).isBetween(3L, 4L));
    }

    @Test
    void matchesExhaustiveMinimumImbalanceForSmallConstrainedSessions() {
        List<ExerciseType> types = List.of(VOCAB_WORD_TO_MEANING, VOCAB_LISTEN_AND_TYPE_WORD, VOCAB_MEANING_TO_SOUND);
        Random random = new Random(31);
        for (int trial = 0; trial < 100; trial++) {
            List<ReviewTargetEligibility> targets = new ArrayList<>();
            for (int index = 0; index < 6; index++) {
                Set<ExerciseType> eligible = EnumSet.noneOf(ExerciseType.class);
                for (ExerciseType type : types) {
                    if (random.nextBoolean()) eligible.add(type);
                }
                if (eligible.isEmpty()) eligible.add(types.get(random.nextInt(types.size())));
                targets.add(new ReviewTargetEligibility("uv-" + index, eligible));
            }
            Map<ExerciseType, Integer> emitted = new EnumMap<>(ExerciseType.class);
            types.forEach(type -> emitted.put(type, random.nextInt(3)));
            var assignments = scheduler.schedule(targets, emitted);
            Map<ExerciseType, Integer> actual = new EnumMap<>(emitted);
            assignments.values().forEach(type -> actual.merge(type, 1, Integer::sum));
            assertThat(cost(actual)).as("trial %s", trial).isEqualTo(minimumCost(targets, 0, emitted));
            targets.forEach(target -> assertThat(target.eligibleTypes()).contains(assignments.get(target.userVocabId())));
        }
    }

    private int minimumCost(List<ReviewTargetEligibility> targets, int index, Map<ExerciseType, Integer> counts) {
        if (index == targets.size()) return cost(counts);
        int minimum = Integer.MAX_VALUE;
        for (ExerciseType type : targets.get(index).eligibleTypes()) {
            Map<ExerciseType, Integer> next = new EnumMap<>(counts);
            next.merge(type, 1, Integer::sum);
            minimum = Math.min(minimum, minimumCost(targets, index + 1, next));
        }
        return minimum;
    }

    private int cost(Map<ExerciseType, Integer> counts) {
        return counts.values().stream().mapToInt(count -> count * count).sum();
    }

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
