package com.bachdauduc.vocab_app.service.review;

import com.bachdauduc.vocab_app.constant.ExerciseType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

@Component
public class BalancedReviewQuizScheduler {
    private final RandomGenerator random;

    public BalancedReviewQuizScheduler() {
        this(new java.util.Random());
    }

    public BalancedReviewQuizScheduler(RandomGenerator random) {
        this.random = random;
    }

    public Map<String, ExerciseType> schedule(List<ReviewTargetEligibility> targets) {
        return schedule(targets, Map.of());
    }

    /** Balance the pending session against quizzes already emitted, which cannot be reassigned. */
    public Map<String, ExerciseType> schedule(
            List<ReviewTargetEligibility> targets, Map<ExerciseType, Integer> emittedCounts
    ) {
        if (targets == null || targets.isEmpty()) {
            return Map.of();
        }

        List<ReviewTargetEligibility> schedulable = targets.stream()
                .filter(target -> target != null && target.userVocabId() != null)
                .filter(target -> !target.eligibleTypes().isEmpty())
                .sorted(Comparator.comparingInt(target -> target.eligibleTypes().size()))
                .toList();
        if (schedulable.isEmpty()) {
            return Map.of();
        }

        Map<ExerciseType, Integer> assigned = new EnumMap<>(ExerciseType.class);
        vocabTypes().forEach(type -> assigned.put(type, emittedCounts.getOrDefault(type, 0)));

        Map<String, ExerciseType> result = new LinkedHashMap<>();
        Map<String, ReviewTargetEligibility> byId = new LinkedHashMap<>();
        for (ReviewTargetEligibility target : schedulable) {
            if (byId.putIfAbsent(target.userVocabId(), target) != null) {
                continue;
            }
            // Follow alternating target/type paths so a previous flexible target can move.
            // Only the terminal type gains a quiz. Choosing its lowest count minimizes the
            // incremental squared-count cost (2 * count + 1) under the eligibility constraints.
            Map<ExerciseType, String> predecessor = new EnumMap<>(ExerciseType.class);
            ArrayDeque<ReviewTargetEligibility> queue = new ArrayDeque<>();
            queue.add(target);
            while (!queue.isEmpty()) {
                ReviewTargetEligibility current = queue.removeFirst();
                for (ExerciseType type : current.eligibleTypes()) {
                    if (predecessor.putIfAbsent(type, current.userVocabId()) == null) {
                        result.forEach((id, previousType) -> {
                            if (previousType == type) {
                                queue.addLast(byId.get(id));
                            }
                        });
                    }
                }
            }
            List<ExerciseType> reachable = new ArrayList<>(predecessor.keySet());
            shuffle(reachable);
            ExerciseType selected = reachable.stream().min(Comparator.comparingInt(assigned::get)).orElseThrow();
            while (selected != null) {
                String id = predecessor.get(selected);
                ExerciseType previous = result.put(id, selected);
                assigned.merge(selected, 1, Integer::sum);
                if (previous != null) {
                    assigned.merge(previous, -1, Integer::sum);
                }
                selected = previous;
            }
        }
        return Map.copyOf(result);
    }

    private List<ExerciseType> vocabTypes() {
        List<ExerciseType> types = new ArrayList<>();
        for (ExerciseType type : ExerciseType.values()) {
            if (type.isVocab()) {
                types.add(type);
            }
        }
        return types;
    }

    private <T> void shuffle(List<T> values) {
        for (int index = values.size() - 1; index > 0; index--) {
            int swapIndex = random.nextInt(index + 1);
            T value = values.get(index);
            values.set(index, values.get(swapIndex));
            values.set(swapIndex, value);
        }
    }
}
