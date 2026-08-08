package com.bachdauduc.vocab_app.service;

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
import com.bachdauduc.vocab_app.service.review.ReviewProgressStore;
import com.bachdauduc.vocab_app.service.review.ReviewQuizFactory;
import com.bachdauduc.vocab_app.service.review.ReviewVocabDataLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

}
