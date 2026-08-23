package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.constant.ExerciseType;
import com.bachdauduc.vocab_app.dto.request.uservocabulary.SubmitReviewAttemptRequest;
import com.bachdauduc.vocab_app.entity.UserVocabAttempt;
import com.bachdauduc.vocab_app.entity.UserVocabulary;
import com.bachdauduc.vocab_app.properties.RedisKeyProperties;
import com.bachdauduc.vocab_app.repository.ListenAndTypeExerciseChallengeRepository;
import com.bachdauduc.vocab_app.repository.UserInfoRepository;
import com.bachdauduc.vocab_app.repository.UserSearchHistoryRepository;
import com.bachdauduc.vocab_app.repository.UserVocabAttemptRepository;
import com.bachdauduc.vocab_app.repository.UserVocabularyRepository;
import com.bachdauduc.vocab_app.repository.WordRepository;
import com.bachdauduc.vocab_app.repository.WordSenseLocalizationRepository;
import com.bachdauduc.vocab_app.repository.WordSenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserVocabularyReviewScheduleServiceTest {
    @Mock UserVocabularyRepository userVocabularyRepository;
    @Mock UserVocabAttemptRepository userVocabAttemptRepository;
    @Mock UserSearchHistoryRepository userSearchHistoryRepository;
    @Mock UserInfoRepository userInfoRepository;
    @Mock WordRepository wordRepository;
    @Mock WordSenseRepository wordSenseRepository;
    @Mock WordSenseLocalizationRepository wordSenseLocalizationRepository;
    @Mock ListenAndTypeExerciseChallengeRepository listenAndTypeExerciseChallengeRepository;
    @Mock GetWordDataService getWordDataService;
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock RedisKeyProperties redisKeyProperties;

    @InjectMocks UserVocabularyService service;

    @BeforeEach
    void setUp() {
        when(userInfoRepository.existsById("user-1")).thenReturn(true);
        when(userVocabularyRepository.existsById("vocab-1")).thenReturn(true);
        when(userVocabAttemptRepository.save(any(UserVocabAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userVocabularyRepository.save(any(UserVocabulary.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @ParameterizedTest
    @CsvSource({"1, 2", "2, 3", "3, 4"})
    void oneCorrectReviewAdvancesEarlyLevels(int currentLevel, int expectedLevel) {
        UserVocabulary vocabulary = vocabulary(currentLevel, 0);
        when(userVocabularyRepository.findById("vocab-1")).thenReturn(Optional.of(vocabulary));

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
        when(userVocabularyRepository.findById("vocab-1")).thenReturn(Optional.of(vocabulary));

        service.submitReviewAttempt(correctRequest());

        assertThat(vocabulary.getLevel()).isEqualTo(expectedLevel);
        assertThat(vocabulary.getCurrentLevelCorrectTurns()).isEqualTo(expectedTurns);
    }

    @Test
    void keepsLevelSixAndContinuesCountingCorrectReviews() {
        UserVocabulary vocabulary = vocabulary(6, 0);
        when(userVocabularyRepository.findById("vocab-1")).thenReturn(Optional.of(vocabulary));

        service.submitReviewAttempt(correctRequest());

        assertThat(vocabulary.getLevel()).isEqualTo(6);
        assertThat(vocabulary.getCurrentLevelCorrectTurns()).isEqualTo(1);
    }

    private UserVocabulary vocabulary(int level, int currentTurns) {
        UserVocabulary vocabulary = new UserVocabulary();
        vocabulary.setId("vocab-1");
        vocabulary.setUserId("user-1");
        vocabulary.setLevel(level);
        vocabulary.setCurrentLevelCorrectTurns(currentTurns);
        return vocabulary;
    }

    private SubmitReviewAttemptRequest correctRequest() {
        SubmitReviewAttemptRequest request = new SubmitReviewAttemptRequest();
        request.setUserId("user-1");
        request.setUserVocabId("vocab-1");
        request.setExerciseType(ExerciseType.VOCAB_WORD_TO_MEANING);
        request.setCorrect(true);
        return request;
    }
}
