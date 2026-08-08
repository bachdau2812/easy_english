package com.bachdauduc.vocab_app.service.review;

import com.bachdauduc.vocab_app.constant.ExerciseType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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

        List<ExerciseType> vocabTypes = vocabTypes();
        Map<ExerciseType, Integer> desired = desiredCounts(schedulable.size(), vocabTypes);
        Map<ExerciseType, Integer> assigned = new EnumMap<>(ExerciseType.class);
        vocabTypes.forEach(type -> assigned.put(type, 0));

        Map<String, ExerciseType> result = new LinkedHashMap<>();
        for (ReviewTargetEligibility target : schedulable) {
            List<ExerciseType> candidates = new ArrayList<>(target.eligibleTypes());
            shuffle(candidates);
            ExerciseType selected = candidates.stream()
                    .max(Comparator
                            .comparingInt((ExerciseType type) -> desired.getOrDefault(type, 0)
                                    - assigned.getOrDefault(type, 0))
                            .thenComparingInt(type -> -assigned.getOrDefault(type, 0)))
                    .orElse(null);
            if (selected != null) {
                result.put(target.userVocabId(), selected);
                assigned.merge(selected, 1, Integer::sum);
            }
        }
        return Map.copyOf(result);
    }

    private Map<ExerciseType, Integer> desiredCounts(int total, List<ExerciseType> types) {
        int base = total / types.size();
        int remainder = total % types.size();
        List<ExerciseType> remainderOrder = new ArrayList<>(types);
        shuffle(remainderOrder);

        Map<ExerciseType, Integer> desired = new EnumMap<>(ExerciseType.class);
        types.forEach(type -> desired.put(type, base));
        for (int index = 0; index < remainder; index++) {
            desired.merge(remainderOrder.get(index), 1, Integer::sum);
        }
        return desired;
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
