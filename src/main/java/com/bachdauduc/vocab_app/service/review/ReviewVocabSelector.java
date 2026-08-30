package com.bachdauduc.vocab_app.service.review;

import com.bachdauduc.vocab_app.entity.UserVocabulary;
import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ReviewVocabSelector {
    public List<UserVocabulary> orderCandidates(
            List<UserVocabulary> availableVocabularies,
            int requested
    ) {
        Map<Integer, Integer> quotas = reviewQuotas(requested);
        if (availableVocabularies == null || availableVocabularies.isEmpty()) {
            return List.of();
        }

        Map<Integer, List<UserVocabulary>> byLevel = availableVocabularies.stream()
                .collect(Collectors.groupingBy(
                        UserVocabulary::getLevel,
                        LinkedHashMap::new,
                        Collectors.toCollection(ArrayList::new)
                ));
        List<UserVocabulary> ordered = new ArrayList<>();
        Set<String> selectedIds = new LinkedHashSet<>();
        int prioritySize = Math.min(requested, availableVocabularies.size());

        while (ordered.size() < prioritySize) {
            int before = ordered.size();
            takeQuotaRound(byLevel, quotas, ordered, selectedIds, prioritySize);
            if (ordered.size() == before) {
                break;
            }
        }

        for (UserVocabulary vocabulary : availableVocabularies) {
            if (selectedIds.add(vocabulary.getId())) {
                ordered.add(vocabulary);
            }
        }
        return List.copyOf(ordered);
    }

    private void takeQuotaRound(
            Map<Integer, List<UserVocabulary>> byLevel,
            Map<Integer, Integer> quotas,
            List<UserVocabulary> ordered,
            Set<String> selectedIds,
            int prioritySize
    ) {
        for (int level = 1; level <= 6 && ordered.size() < prioritySize; level++) {
            int taken = 0;
            for (UserVocabulary vocabulary : byLevel.getOrDefault(level, List.of())) {
                if (taken >= quotas.getOrDefault(level, 0) || ordered.size() >= prioritySize) {
                    break;
                }
                if (selectedIds.add(vocabulary.getId())) {
                    ordered.add(vocabulary);
                    taken++;
                }
            }
        }
    }

    private Map<Integer, Integer> reviewQuotas(int requested) {
        return switch (requested) {
            case 30 -> Map.of(1, 9, 2, 8, 3, 5, 4, 3, 5, 3, 6, 2);
            case 60 -> Map.of(1, 15, 2, 15, 3, 10, 4, 10, 5, 5, 6, 5);
            case 90 -> Map.of(1, 25, 2, 25, 3, 14, 4, 13, 5, 10, 6, 3);
            default -> throw new AppException(ErrorCode.INVALID_REVIEW_VOCAB_TOTAL);
        };
    }
}
