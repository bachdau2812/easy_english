package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.constant.ExerciseType;
import com.bachdauduc.vocab_app.dto.response.exercise.VocabReviewQuizResponse;
import com.bachdauduc.vocab_app.entity.UserVocabulary;
import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import com.bachdauduc.vocab_app.repository.UserInfoRepository;
import com.bachdauduc.vocab_app.repository.UserVocabularyRepository;
import com.bachdauduc.vocab_app.service.review.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewSessionBalanceTest {
    @Mock UserInfoRepository userInfoRepository;
    @Mock UserVocabularyRepository userVocabularyRepository;
    @Mock ReviewAvailabilityService reviewAvailabilityService;
    @Mock WordExampleGenerationService wordExampleGenerationService;
    @Mock ReviewVocabDataLoader reviewVocabDataLoader;
    @Mock ReviewQuizFactory reviewQuizFactory;
    @Mock ReviewProgressStore reviewProgressStore;
    @InjectMocks ExerciseService service;

    private final Set<ExerciseType> allTypes = EnumSet.allOf(ExerciseType.class).stream()
            .filter(ExerciseType::isVocab).collect(Collectors.toSet());

    @Test
    void balancesTheActualSmallerPoolWhenFewerThanQuotaAreAvailable() {
        Set<String> available = prepare(30, false).subList(0, 9).stream()
                .map(UserVocabulary::getId).collect(Collectors.toSet());
        doAnswer(invocation -> available.contains(invocation.<String>getArgument(1)) ? allTypes : Set.of())
                .when(reviewProgressStore).availableTypes(anyString(), anyString(), any());
        var quizzes = service.getReviewVocabs("user", 30, "vi");
        assertThat(quizzes).hasSize(9);
        assertThat(counts(quizzes)).hasSize(8);
        assertThat(counts(quizzes).values()).allSatisfy(count -> assertThat(count).isBetween(1L, 2L));
    }

    @Test
    void stopsWhenAllRemainingReservationsConflict() {
        String candidate = prepare(30, false).getFirst().getId();
        doAnswer(invocation -> candidate.equals(invocation.getArgument(1)) ? allTypes : Set.of())
                .when(reviewProgressStore).availableTypes(anyString(), anyString(), any());
        doReturn(Optional.empty()).when(reviewProgressStore).reserveFirstAvailable(anyString(), anyString(), any());
        assertThat(service.getReviewVocabs("user", 30, "vi")).isEmpty();
        verify(reviewProgressStore, times(8)).reserveFirstAvailable(eq("user"), eq(candidate), any());
        verify(reviewQuizFactory, never()).create(any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 60, 90})
    void balancesReturnedSessionWithoutLettingOverflowSkewItsQuotas(int quota) {
        List<UserVocabulary> selected = prepare(quota, true);
        var quizzes = service.getReviewVocabs("user", quota, "vi");
        assertBalanced(quizzes, quota);
        assertThat(quizzes).extracting(VocabReviewQuizResponse::getUserVocabId)
                .containsExactlyElementsOf(selected.stream().map(UserVocabulary::getId).toList());
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 60, 90})
    void balancesOnlyTypesStillAvailableInRedis(int quota) {
        prepare(quota, false);
        Set<ExerciseType> remaining = Set.of(ExerciseType.VOCAB_WORD_TO_MEANING, ExerciseType.VOCAB_FILL_MISSING_WORD_PART);
        when(reviewProgressStore.availableTypes(anyString(), anyString(), any())).thenReturn(remaining);
        doAnswer(invocation -> {
            List<ExerciseType> candidates = invocation.getArgument(2);
            return candidates.stream().filter(remaining::contains).findFirst();
        }).when(reviewProgressStore).reserveFirstAvailable(anyString(), anyString(), any());
        var quizzes = service.getReviewVocabs("user", quota, "vi");
        assertThat(quizzes).hasSize(quota);
        assertThat(counts(quizzes)).containsOnlyKeys(remaining);
        assertThat(counts(quizzes).values()).containsOnly((long) quota / 2);
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 60, 90})
    void replansAfterReservationConflictWithoutLosingBalance(int quota) {
        prepare(quota, false);
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            List<ExerciseType> candidates = invocation.getArgument(2);
            return attempts.incrementAndGet() == quota / 2 ? Optional.empty() : Optional.of(candidates.getFirst());
        }).when(reviewProgressStore).reserveFirstAvailable(anyString(), anyString(), any());
        assertBalanced(service.getReviewVocabs("user", quota, "vi"), quota);
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 60, 90})
    void balancesBackfillWhenASelectedVocabularyBecomesUnavailable(int quota) {
        String exhausted = prepare(quota, false).get(quota / 2).getId();
        doAnswer(invocation -> {
            List<ExerciseType> candidates = invocation.getArgument(2);
            return exhausted.equals(invocation.getArgument(1)) ? Optional.empty() : Optional.of(candidates.getFirst());
        }).when(reviewProgressStore).reserveFirstAvailable(anyString(), anyString(), any());
        var quizzes = service.getReviewVocabs("user", quota, "vi");
        assertBalanced(quizzes, quota);
        assertThat(quizzes).extracting(VocabReviewQuizResponse::getUserVocabId).doesNotContain(exhausted).doesNotHaveDuplicates();
    }

    @ParameterizedTest
    @ValueSource(ints = {30, 60, 90})
    void replansAfterQuizCreationFails(int quota) {
        prepare(quota, false);
        AtomicBoolean failure = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (failure.getAndSet(false)) {
                throw new AppException(ErrorCode.WORD_EXAMPLE_NOT_FOUND);
            }
            return response(invocation.getArgument(0), invocation.getArgument(3));
        }).when(reviewQuizFactory).create(any(), any(), any(), any());
        assertBalanced(service.getReviewVocabs("user", quota, "vi"), quota);
        verify(reviewProgressStore).release(eq("user"), anyString(), any());
    }

    private List<UserVocabulary> prepare(int quota, boolean constrainedOverflow) {
        var selector = new ReviewVocabSelector();
        ReflectionTestUtils.setField(service, "reviewVocabSelector", selector);
        ReflectionTestUtils.setField(service, "balancedReviewQuizScheduler", new BalancedReviewQuizScheduler(new Random(7)));
        List<UserVocabulary> due = new ArrayList<>();
        Map<String, ReviewVocabSnapshot> snapshots = new LinkedHashMap<>();
        for (int level = 1; level <= 6; level++) {
            for (int index = 0; index < 40; index++) {
                var vocabulary = new UserVocabulary();
                vocabulary.setId(level + "-" + index);
                vocabulary.setWordId(vocabulary.getId());
                vocabulary.setLevel(level);
                due.add(vocabulary);
                snapshots.put(vocabulary.getId(), new ReviewVocabSnapshot(ReviewVocabSnapshot.CURRENT_SCHEMA_VERSION,
                        vocabulary.getId(), "sense", "vi", "word", "noun", "meaning", null, List.of(), List.of(), Instant.now()));
            }
        }
        var selected = selector.orderCandidates(due, quota).subList(0, quota);
        var selectedIds = selected.stream().map(UserVocabulary::getId).collect(Collectors.toSet());
        when(userInfoRepository.existsById("user")).thenReturn(true);
        when(userVocabularyRepository.findDueReviewVocabs(eq("user"), any(LocalDateTime.class))).thenReturn(due);
        when(reviewAvailabilityService.findAvailable("user", due, "vi")).thenReturn(due);
        when(reviewVocabDataLoader.load(any(), eq("vi"))).thenReturn(snapshots);
        when(reviewQuizFactory.eligibleTypes(any(), any(), any())).thenAnswer(invocation ->
                constrainedOverflow && !selectedIds.contains(invocation.<UserVocabulary>getArgument(0).getId())
                        ? Set.of(ExerciseType.VOCAB_WORD_TO_MEANING) : allTypes);
        lenient().when(reviewProgressStore.availableTypes(anyString(), anyString(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        lenient().when(reviewProgressStore.reserveFirstAvailable(anyString(), anyString(), any())).thenAnswer(invocation ->
                Optional.of(invocation.<List<ExerciseType>>getArgument(2).getFirst()));
        lenient().when(reviewQuizFactory.create(any(), any(), any(), any())).thenAnswer(invocation ->
                response(invocation.getArgument(0), invocation.getArgument(3)));
        return selected;
    }

    private VocabReviewQuizResponse response(UserVocabulary vocabulary, ExerciseType type) {
        return VocabReviewQuizResponse.builder().userVocabId(vocabulary.getId()).exerciseType(type).build();
    }

    private Map<ExerciseType, Long> counts(List<VocabReviewQuizResponse> quizzes) {
        return quizzes.stream().collect(Collectors.groupingBy(VocabReviewQuizResponse::getExerciseType, Collectors.counting()));
    }

    private void assertBalanced(List<VocabReviewQuizResponse> quizzes, int quota) {
        assertThat(quizzes).hasSize(quota);
        var counts = counts(quizzes);
        assertThat(counts).hasSize(8);
        assertThat(counts.values()).allSatisfy(count -> assertThat(count).isBetween((long) quota / 8, (long) (quota + 7) / 8));
    }
}
