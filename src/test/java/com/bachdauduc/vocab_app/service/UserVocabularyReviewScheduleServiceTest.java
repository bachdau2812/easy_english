package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.constant.ExerciseType;
import com.bachdauduc.vocab_app.dto.request.uservocabulary.SubmitReviewAttemptRequest;
import com.bachdauduc.vocab_app.entity.UserVocabAttempt;
import com.bachdauduc.vocab_app.entity.UserVocabulary;
import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import com.bachdauduc.vocab_app.repository.ListenAndTypeExerciseChallengeRepository;
import com.bachdauduc.vocab_app.repository.UserInfoRepository;
import com.bachdauduc.vocab_app.repository.UserSearchHistoryRepository;
import com.bachdauduc.vocab_app.repository.UserVocabAttemptRepository;
import com.bachdauduc.vocab_app.repository.UserVocabularyRepository;
import com.bachdauduc.vocab_app.repository.WordRepository;
import com.bachdauduc.vocab_app.repository.WordSenseLocalizationRepository;
import com.bachdauduc.vocab_app.repository.WordSenseRepository;
import com.bachdauduc.vocab_app.service.review.ReviewAvailabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserVocabularyReviewScheduleServiceTest {
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-30T00:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 30, 7, 0);

    @Mock UserVocabularyRepository userVocabularyRepository;
    @Mock UserVocabAttemptRepository userVocabAttemptRepository;
    @Mock UserSearchHistoryRepository userSearchHistoryRepository;
    @Mock UserInfoRepository userInfoRepository;
    @Mock WordRepository wordRepository;
    @Mock WordSenseRepository wordSenseRepository;
    @Mock WordSenseLocalizationRepository wordSenseLocalizationRepository;
    @Mock ListenAndTypeExerciseChallengeRepository listenAndTypeExerciseChallengeRepository;
    @Mock GetWordDataService getWordDataService;
    @Mock ReviewAvailabilityService reviewAvailabilityService;
    @Mock Clock clock;

    @InjectMocks UserVocabularyService service;

    @BeforeEach
    void setUp() {
        lenient().when(userInfoRepository.existsById("user-1")).thenReturn(true);
        lenient().when(userVocabularyRepository.existsById("vocab-1")).thenReturn(true);
        lenient().when(clock.instant()).thenReturn(FIXED_INSTANT);
        lenient().when(clock.getZone()).thenReturn(APP_ZONE);
        lenient().when(userVocabAttemptRepository.save(any(UserVocabAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(userVocabularyRepository.save(any(UserVocabulary.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @ParameterizedTest
    @CsvSource({"1, 2", "2, 3", "3, 4"})
    void oneCorrectReviewAdvancesEarlyLevels(int currentLevel, int expectedLevel) {
        UserVocabulary vocabulary = vocabulary(currentLevel, 0);
        stubVocabulary(vocabulary);

        service.submitReviewAttempt(correctRequest());

        assertThat(vocabulary.getLevel()).isEqualTo(expectedLevel);
        assertThat(vocabulary.getCurrentLevelCorrectTurns()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "4, 0, 4, 1",
            "4, 1, 5, 0",
            "5, 2, 5, 3",
            "5, 3, 6, 0"
    })
    void preservesHigherLevelThresholds(
            int currentLevel,
            int currentTurns,
            int expectedLevel,
            int expectedTurns
    ) {
        UserVocabulary vocabulary = vocabulary(currentLevel, currentTurns);
        stubVocabulary(vocabulary);

        service.submitReviewAttempt(correctRequest());

        assertThat(vocabulary.getLevel()).isEqualTo(expectedLevel);
        assertThat(vocabulary.getCurrentLevelCorrectTurns()).isEqualTo(expectedTurns);
    }

    @Test
    void keepsLevelSixAndContinuesCountingCorrectReviews() {
        UserVocabulary vocabulary = vocabulary(6, 0);
        stubVocabulary(vocabulary);

        service.submitReviewAttempt(correctRequest());

        assertThat(vocabulary.getLevel()).isEqualTo(6);
        assertThat(vocabulary.getCurrentLevelCorrectTurns()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({
            "4, 0, 4, 1, 14",
            "4, 1, 5, 0, 72",
            "5, 2, 5, 3, 24",
            "5, 3, 6, 0, 336"
    })
    void usesExactHigherLevelCorrectIntervals(
            int currentLevel,
            int currentTurns,
            int expectedLevel,
            int expectedTurns,
            long expectedHours
    ) {
        UserVocabulary vocabulary = dueVocabulary(currentLevel, currentTurns);
        stubVocabulary(vocabulary);

        service.submitReviewAttempt(correctRequest());

        assertThat(vocabulary.getLevel()).isEqualTo(expectedLevel);
        assertThat(vocabulary.getCurrentLevelCorrectTurns()).isEqualTo(expectedTurns);
        assertThat(vocabulary.getNextReviewAt()).isEqualTo(NOW.plusHours(expectedHours));
    }

    @ParameterizedTest
    @CsvSource({"4, 12", "5, 24"})
    void usesExactHigherLevelWrongIntervals(int level, long expectedHours) {
        UserVocabulary vocabulary = dueVocabulary(level, 1);
        stubVocabulary(vocabulary);
        service.submitReviewAttempt(reviewRequest(false));

        assertThat(vocabulary.getLevel()).isEqualTo(level);
        assertThat(vocabulary.getCurrentLevelCorrectTurns()).isZero();
        assertThat(vocabulary.getNextReviewAt()).isEqualTo(NOW.plusHours(expectedHours));
    }

    @ParameterizedTest
    @CsvSource({"0, 1, 14", "1, 2, 30", "2, 3, 60", "3, 4, 90"})
    void usesExactLevelSixIntervals(int currentTurns, int expectedTurns, long expectedDays) {
        UserVocabulary vocabulary = dueVocabulary(6, currentTurns);
        stubVocabulary(vocabulary);

        service.submitReviewAttempt(correctRequest());

        assertThat(vocabulary.getLevel()).isEqualTo(6);
        assertThat(vocabulary.getCurrentLevelCorrectTurns()).isEqualTo(expectedTurns);
        assertThat(vocabulary.getNextReviewAt()).isEqualTo(NOW.plusDays(expectedDays));
    }

    @Test
    void wrongThenCorrectRetryKeepsWrongScheduleForTheTurn() {
        UserVocabulary vocabulary = dueVocabulary(4, 1);
        stubVocabulary(vocabulary);
        service.submitReviewAttempt(reviewRequest(false));
        service.submitReviewAttempt(correctRequest());

        assertThat(vocabulary.getLevel()).isEqualTo(4);
        assertThat(vocabulary.getCurrentLevelCorrectTurns()).isZero();
        assertThat(vocabulary.getNextReviewAt()).isEqualTo(NOW.plusHours(12));
    }

    @Test
    void duplicateCorrectRetryDoesNotAdvanceOrExtendScheduleAgain() {
        UserVocabulary vocabulary = dueVocabulary(4, 0);
        stubVocabulary(vocabulary);

        service.submitReviewAttempt(correctRequest());
        service.submitReviewAttempt(correctRequest());

        assertThat(vocabulary.getLevel()).isEqualTo(4);
        assertThat(vocabulary.getCurrentLevelCorrectTurns()).isEqualTo(1);
        assertThat(vocabulary.getNextReviewAt()).isEqualTo(NOW.plusHours(14));
    }

    @Test
    void rejectsVocabularyOwnedByAnotherUserBeforeSavingAttempt() {
        UserVocabulary vocabulary = dueVocabulary(4, 0);
        vocabulary.setUserId("other-user");
        stubVocabulary(vocabulary);

        assertThatThrownBy(() -> service.submitReviewAttempt(correctRequest()))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.USER_VOCABULARY_NOT_FOUND));
        verify(userVocabAttemptRepository, never()).save(any(UserVocabAttempt.class));
        verify(userVocabularyRepository, never()).save(any(UserVocabulary.class));
    }

    private UserVocabulary vocabulary(int level, int currentTurns) {
        UserVocabulary vocabulary = new UserVocabulary();
        vocabulary.setId("vocab-1");
        vocabulary.setUserId("user-1");
        vocabulary.setLevel(level);
        vocabulary.setCurrentLevelCorrectTurns(currentTurns);
        return vocabulary;
    }

    private UserVocabulary dueVocabulary(int level, int currentTurns) {
        UserVocabulary vocabulary = vocabulary(level, currentTurns);
        vocabulary.setNextReviewAt(NOW.minusMinutes(1));
        return vocabulary;
    }

    private void stubVocabulary(UserVocabulary vocabulary) {
        when(userVocabularyRepository.findByIdForUpdate("vocab-1"))
                .thenReturn(Optional.of(vocabulary));
    }

    private SubmitReviewAttemptRequest correctRequest() {
        return reviewRequest(true);
    }

    private SubmitReviewAttemptRequest reviewRequest(boolean correct) {
        SubmitReviewAttemptRequest request = new SubmitReviewAttemptRequest();
        request.setUserId("user-1");
        request.setUserVocabId("vocab-1");
        request.setExerciseType(ExerciseType.VOCAB_WORD_TO_MEANING);
        request.setCorrect(correct);
        return request;
    }
}
