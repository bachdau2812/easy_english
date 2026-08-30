package com.bachdauduc.vocab_app.service.review;

import com.bachdauduc.vocab_app.entity.UserVocabulary;
import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewVocabSelectorTest {
    ReviewVocabSelector selector = new ReviewVocabSelector();

    @Test
    void appliesThirtyWordLevelQuotas() {
        assertThat(firstBatchCounts(selector.orderCandidates(vocabularies(30), 30), 30))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        1, 9L, 2, 8L, 3, 5L, 4, 3L, 5, 3L, 6, 2L
                ));
    }

    @Test
    void appliesSixtyWordLevelQuotas() {
        assertThat(firstBatchCounts(selector.orderCandidates(vocabularies(30), 60), 60))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        1, 15L, 2, 15L, 3, 10L, 4, 10L, 5, 5L, 6, 5L
                ));
    }

    @Test
    void appliesNinetyWordQuotasThatSumToNinety() {
        assertThat(firstBatchCounts(selector.orderCandidates(vocabularies(30), 90), 90))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        1, 25L, 2, 25L, 3, 14L, 4, 13L, 5, 10L, 6, 3L
                ));
    }

    @Test
    void redistributesShortagesAndRetainsOverflowForBackfill() {
        List<UserVocabulary> available = new ArrayList<>();
        available.addAll(vocabulariesAtLevel(4, 40));
        available.addAll(vocabulariesAtLevel(5, 10));

        List<UserVocabulary> ordered = selector.orderCandidates(available, 30);

        assertThat(ordered).hasSize(50);
        assertThat(firstBatchCounts(ordered, 30))
                .containsExactlyInAnyOrderEntriesOf(Map.of(4, 20L, 5, 10L));
        assertThat(ordered.stream().map(UserVocabulary::getId)).doesNotHaveDuplicates();
    }

    @Test
    void rejectsUnsupportedBatchSize() {
        assertThatThrownBy(() -> selector.orderCandidates(vocabularies(1), 20))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_REVIEW_VOCAB_TOTAL));
    }

    private Map<Integer, Long> firstBatchCounts(
            List<UserVocabulary> ordered,
            int batchSize
    ) {
        return ordered.stream()
                .limit(batchSize)
                .collect(Collectors.groupingBy(UserVocabulary::getLevel, Collectors.counting()));
    }

    private List<UserVocabulary> vocabularies(int perLevel) {
        List<UserVocabulary> vocabularies = new ArrayList<>();
        for (int level = 1; level <= 6; level++) {
            vocabularies.addAll(vocabulariesAtLevel(level, perLevel));
        }
        return vocabularies;
    }

    private List<UserVocabulary> vocabulariesAtLevel(int level, int count) {
        List<UserVocabulary> vocabularies = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            UserVocabulary vocabulary = new UserVocabulary();
            vocabulary.setId("level-" + level + "-vocab-" + index);
            vocabulary.setLevel(level);
            vocabularies.add(vocabulary);
        }
        return vocabularies;
    }
}
