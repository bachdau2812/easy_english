package com.bachdauduc.vocab_app.service.review;

import com.bachdauduc.vocab_app.entity.UserVocabulary;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ReviewRequestContext {
    private final Map<String, UserVocabulary> userVocabById;
    private final Map<String, ReviewVocabSnapshot> snapshotByUserVocabId;
    private final List<String> userVocabIds;
    private final List<String> meaningDistractors;
    private final List<String> wordDistractors;
    private final List<String> soundDistractors;

    private ReviewRequestContext(
            Map<String, UserVocabulary> userVocabById,
            Map<String, ReviewVocabSnapshot> snapshotByUserVocabId
    ) {
        this.userVocabById = Map.copyOf(userVocabById);
        this.snapshotByUserVocabId = Map.copyOf(snapshotByUserVocabId);
        this.userVocabIds = List.copyOf(userVocabById.keySet());
        this.meaningDistractors = distinctValues(
                snapshotByUserVocabId.values().stream().map(ReviewVocabSnapshot::meaning).toList());
        this.wordDistractors = distinctValues(
                snapshotByUserVocabId.values().stream().map(ReviewVocabSnapshot::word).toList());
        this.soundDistractors = distinctValues(snapshotByUserVocabId.values().stream()
                .map(ReviewVocabSnapshot::playableSoundUrl)
                .flatMap(java.util.Optional::stream)
                .toList());
    }

    public static ReviewRequestContext create(
            List<UserVocabulary> vocabularies,
            Map<String, ReviewVocabSnapshot> snapshotsByUserVocabId
    ) {
        Map<String, UserVocabulary> ordered = vocabularies.stream()
                .filter(vocabulary -> vocabulary != null && snapshotsByUserVocabId.containsKey(vocabulary.getId()))
                .collect(Collectors.toMap(
                        UserVocabulary::getId,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        Map<String, ReviewVocabSnapshot> orderedSnapshots = new LinkedHashMap<>();
        ordered.keySet().forEach(id -> orderedSnapshots.put(id, snapshotsByUserVocabId.get(id)));
        return new ReviewRequestContext(ordered, orderedSnapshots);
    }

    public UserVocabulary vocabulary(String userVocabId) {
        return userVocabById.get(userVocabId);
    }

    public ReviewVocabSnapshot snapshot(String userVocabId) {
        return snapshotByUserVocabId.get(userVocabId);
    }

    public List<String> userVocabIds() {
        return userVocabIds;
    }

    public List<String> meaningDistractors() {
        return meaningDistractors;
    }

    public List<String> wordDistractors() {
        return wordDistractors;
    }

    public List<String> soundDistractors() {
        return soundDistractors;
    }

    private List<String> distinctValues(List<String> values) {
        return List.copyOf(values.stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }
}
