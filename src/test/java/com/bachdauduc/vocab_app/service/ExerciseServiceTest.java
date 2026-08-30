package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.constant.ExerciseType;
import com.bachdauduc.vocab_app.dto.response.exercise.VocabReviewQuizResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordSenseResponse;
import com.bachdauduc.vocab_app.entity.UserVocabulary;
import com.bachdauduc.vocab_app.properties.RedisKeyProperties;
import com.bachdauduc.vocab_app.repository.ListenAndTypeExerciseChallengeRepository;
import com.bachdauduc.vocab_app.repository.ListenAndTypeSubCategoryRepository;
import com.bachdauduc.vocab_app.repository.ListenExerciseRepository;
import com.bachdauduc.vocab_app.repository.ListeningCategoryRepository;
import com.bachdauduc.vocab_app.repository.UserInfoRepository;
import com.bachdauduc.vocab_app.repository.UserLessonRepository;
import com.bachdauduc.vocab_app.repository.UserVocabAttemptRepository;
import com.bachdauduc.vocab_app.repository.UserVocabularyRepository;
import com.bachdauduc.vocab_app.repository.WordExampleRepository;
import com.bachdauduc.vocab_app.repository.WordRepository;
import com.bachdauduc.vocab_app.repository.WordSenseLocalizationRepository;
import com.bachdauduc.vocab_app.repository.WordSenseRepository;
import com.bachdauduc.vocab_app.repository.WordSoundRepository;
import com.bachdauduc.vocab_app.service.review.BalancedReviewQuizScheduler;
import com.bachdauduc.vocab_app.service.review.ReviewAvailabilityService;
import com.bachdauduc.vocab_app.service.review.ReviewProgressStore;
import com.bachdauduc.vocab_app.service.review.ReviewQuizFactory;
import com.bachdauduc.vocab_app.service.review.ReviewVocabDataLoader;
import com.bachdauduc.vocab_app.service.review.ReviewVocabSelector;
import com.bachdauduc.vocab_app.service.review.ReviewVocabSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExerciseServiceTest {
    @Mock UserVocabularyRepository userVocabularyRepository;
    @Mock UserLessonRepository userLessonRepository;
    @Mock UserVocabAttemptRepository userVocabAttemptRepository;
    @Mock UserInfoRepository userInfoRepository;
    @Mock ListenExerciseRepository listenExerciseRepository;
    @Mock ListeningCategoryRepository listeningCategoryRepository;
    @Mock ListenAndTypeExerciseChallengeRepository listenAndTypeExerciseChallengeRepository;
    @Mock ListenAndTypeSubCategoryRepository listenAndTypeSubCategoryRepository;
    @Mock WordRepository wordRepository;
    @Mock WordSenseRepository wordSenseRepository;
    @Mock WordSenseLocalizationRepository wordSenseLocalizationRepository;
    @Mock WordSoundRepository wordSoundRepository;
    @Mock WordExampleRepository wordExampleRepository;
    @Mock WordExampleGenerationService wordExampleGenerationService;
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock RedisKeyProperties redisKeyProperties;
    @Mock ReviewVocabDataLoader reviewVocabDataLoader;
    @Mock BalancedReviewQuizScheduler balancedReviewQuizScheduler;
    @Mock ReviewQuizFactory reviewQuizFactory;
    @Mock ReviewProgressStore reviewProgressStore;
    @Mock ReviewAvailabilityService reviewAvailabilityService;
    @Mock ReviewVocabSelector reviewVocabSelector;

    @InjectMocks ExerciseService service;

    UserVocabulary vocabulary;

    @BeforeEach
    void setUp() {
        vocabulary = new UserVocabulary();
        vocabulary.setId("user-vocab-1");
        vocabulary.setUserId("user-1");
        vocabulary.setWordId("word-1");
        vocabulary.setSenseId("sense-1");
        vocabulary.setLevel(1);

        when(userInfoRepository.existsById("user-1")).thenReturn(true);
        when(balancedReviewQuizScheduler.schedule(any())).thenReturn(java.util.Map.of());
    }

    @Test
    void getReviewVocabsPreflightsAllSelectedVocabulariesAsOneBatch() {
        when(userVocabularyRepository.findDueReviewVocabs(eq("user-1"), any(LocalDateTime.class)))
                .thenReturn(List.of(vocabulary));
        when(reviewAvailabilityService.findAvailable("user-1", List.of(vocabulary), "vi"))
                .thenReturn(List.of(vocabulary));
        when(reviewVocabSelector.orderCandidates(List.of(vocabulary), 30))
                .thenReturn(List.of(vocabulary));
        when(reviewVocabDataLoader.load(List.of(vocabulary), "vi")).thenReturn(java.util.Map.of());

        assertThat(service.getReviewVocabs("user-1", 30, "vi")).isEmpty();

        verify(wordExampleGenerationService).ensureExamples(List.of(vocabulary));
        verify(reviewVocabDataLoader).load(List.of(vocabulary), "vi");
    }

    @Test
    void getReviewVocabPreflightsTheRequestedVocabulary() {
        when(userVocabularyRepository.findById("user-vocab-1")).thenReturn(Optional.of(vocabulary));
        when(userVocabularyRepository.findDueReviewVocabs(
                eq("user-1"),
                any(LocalDateTime.class),
                any(org.springframework.data.domain.Pageable.class)
        ))
                .thenReturn(List.of());
        when(reviewVocabDataLoader.load(List.of(vocabulary), "vi")).thenReturn(java.util.Map.of());

        assertThat(service.getReviewVocab("user-1", "user-vocab-1", "vi")).isEmpty();

        verify(wordExampleGenerationService).ensureExamples(List.of(vocabulary));
        verify(reviewVocabDataLoader).load(List.of(vocabulary), "vi");
    }

    @Test
    void backfillsAReservationConflictFromOverflowCandidates() {
        List<UserVocabulary> due = java.util.stream.IntStream.rangeClosed(1, 31)
                .mapToObj(index -> vocabulary(index, 4))
                .toList();
        when(userVocabularyRepository.findDueReviewVocabs(eq("user-1"), any(LocalDateTime.class)))
                .thenReturn(due);
        when(reviewAvailabilityService.findAvailable("user-1", due, "vi")).thenReturn(due);
        when(reviewVocabSelector.orderCandidates(due, 30)).thenReturn(due);
        when(reviewVocabDataLoader.load(any(), eq("vi"))).thenAnswer(invocation -> {
            List<UserVocabulary> loaded = invocation.getArgument(0);
            Map<String, ReviewVocabSnapshot> snapshots = new LinkedHashMap<>();
            loaded.forEach(item -> snapshots.put(item.getId(), snapshot(item)));
            return snapshots;
        });
        when(reviewQuizFactory.eligibleTypes(any(), any(), any()))
                .thenReturn(Set.of(ExerciseType.VOCAB_WORD_TO_MEANING));
        when(balancedReviewQuizScheduler.schedule(any())).thenAnswer(invocation -> {
            List<com.bachdauduc.vocab_app.service.review.ReviewTargetEligibility> targets =
                    invocation.getArgument(0);
            Map<String, ExerciseType> assignments = new LinkedHashMap<>();
            targets.forEach(target -> assignments.put(
                    target.userVocabId(),
                    ExerciseType.VOCAB_WORD_TO_MEANING
            ));
            return assignments;
        });
        when(reviewProgressStore.reserveFirstAvailable(eq("user-1"), anyString(), any()))
                .thenAnswer(invocation -> "vocab-1".equals(invocation.<String>getArgument(1))
                        ? Optional.empty()
                        : Optional.of(ExerciseType.VOCAB_WORD_TO_MEANING));
        when(reviewQuizFactory.create(any(), any(), any(), eq(ExerciseType.VOCAB_WORD_TO_MEANING)))
                .thenAnswer(invocation -> {
                    UserVocabulary target = invocation.getArgument(0);
                    return VocabReviewQuizResponse.builder()
                            .userVocabId(target.getId())
                            .wordId(target.getWordId())
                            .exerciseType(ExerciseType.VOCAB_WORD_TO_MEANING)
                            .build();
                });

        assertThat(service.getReviewVocabs("user-1", 30, "vi"))
                .hasSize(30)
                .extracting(VocabReviewQuizResponse::getUserVocabId)
                .contains("vocab-31");
    }

    private UserVocabulary vocabulary(int index, int level) {
        UserVocabulary item = new UserVocabulary();
        item.setId("vocab-" + index);
        item.setUserId("user-1");
        item.setWordId("word-" + index);
        item.setSenseId("sense-" + index);
        item.setLevel(level);
        return item;
    }

    private ReviewVocabSnapshot snapshot(UserVocabulary item) {
        return new ReviewVocabSnapshot(
                ReviewVocabSnapshot.CURRENT_SCHEMA_VERSION,
                item.getWordId(),
                "sense:" + item.getSenseId(),
                "vi",
                "word-" + item.getId(),
                "noun",
                "meaning-" + item.getId(),
                WordSenseResponse.builder().senseId(item.getSenseId()).build(),
                List.of(),
                List.of(),
                Instant.parse("2026-08-30T00:00:00Z")
        );
    }

}
