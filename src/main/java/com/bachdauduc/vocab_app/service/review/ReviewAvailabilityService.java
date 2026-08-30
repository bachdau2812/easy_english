package com.bachdauduc.vocab_app.service.review;

import com.bachdauduc.vocab_app.constant.ExerciseType;
import com.bachdauduc.vocab_app.entity.UserVocabulary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewAvailabilityService {
    private final ReviewVocabDataLoader reviewVocabDataLoader;
    private final ReviewQuizFactory reviewQuizFactory;
    private final ReviewProgressStore reviewProgressStore;

    public List<UserVocabulary> findAvailable(
            String userId,
            List<UserVocabulary> dueVocabularies,
            String langCode
    ) {
        if (dueVocabularies == null || dueVocabularies.isEmpty()) {
            return List.of();
        }

        Map<String, ReviewVocabSnapshot> snapshots =
                reviewVocabDataLoader.load(dueVocabularies, langCode);
        ReviewRequestContext context = ReviewRequestContext.create(dueVocabularies, snapshots);
        List<UserVocabulary> available = new ArrayList<>();
        int missingSnapshotCount = 0;
        int ineligibleCount = 0;
        int exhaustedCount = 0;

        for (UserVocabulary vocabulary : dueVocabularies) {
            ReviewVocabSnapshot snapshot = snapshots.get(vocabulary.getId());
            if (snapshot == null) {
                missingSnapshotCount++;
                continue;
            }
            Set<ExerciseType> eligibleTypes = reviewQuizFactory.eligibleTypes(
                    vocabulary,
                    snapshot,
                    context
            );
            if (eligibleTypes.isEmpty()) {
                ineligibleCount++;
                continue;
            }
            if (reviewProgressStore.availableTypes(
                    userId,
                    vocabulary.getId(),
                    eligibleTypes
            ).isEmpty()) {
                exhaustedCount++;
                continue;
            }
            available.add(vocabulary);
        }

        log.debug("Review availability evaluated: userId={}, dueCount={}, availableCount={}, missingSnapshotCount={}, ineligibleCount={}, exhaustedCount={}",
                userId, dueVocabularies.size(), available.size(), missingSnapshotCount,
                ineligibleCount, exhaustedCount);
        return List.copyOf(available);
    }
}
