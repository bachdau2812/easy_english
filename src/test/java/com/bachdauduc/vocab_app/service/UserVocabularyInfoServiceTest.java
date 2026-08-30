package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.constant.UserVocabularyInfoType;
import com.bachdauduc.vocab_app.dto.response.uservocabulary.UserVocabularyInfoResponse;
import com.bachdauduc.vocab_app.dto.response.uservocabulary.UserVocabularyLevelQuantityResponse;
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
import com.bachdauduc.vocab_app.repository.projection.UserVocabularyLevelQuantityProjection;
import com.bachdauduc.vocab_app.service.review.ReviewAvailabilityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static com.bachdauduc.vocab_app.constant.UserVocabularyInfoType.VOCAB_QUANTITY;
import static com.bachdauduc.vocab_app.constant.UserVocabularyInfoType.VOCAB_REVIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserVocabularyInfoServiceTest {
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

    @InjectMocks UserVocabularyService service;

    @Test
    void returnsTotalAndAllSixLevelsForVocabQuantity() {
        when(userInfoRepository.existsById("user-1")).thenReturn(true);
        List<UserVocabularyLevelQuantityProjection> quantities = List.of(
                projection(1, 2L),
                projection(3, 3L),
                projection(8, 5L)
        );
        when(userVocabularyRepository.countUserVocabularyByLevel("user-1"))
                .thenReturn(quantities);

        UserVocabularyInfoResponse response =
                service.getUserVocabularyInfo("user-1", " vocab_quantity ");

        assertThat(response.getUserId()).isEqualTo("user-1");
        assertThat(response.getInfoType()).isEqualTo(VOCAB_QUANTITY);
        assertThat(response.getTotalQuantity()).isEqualTo(10L);
        assertThat(response.getQuantityByLevels())
                .extracting(
                        UserVocabularyLevelQuantityResponse::getLevel,
                        UserVocabularyLevelQuantityResponse::getQuantity
                )
                .containsExactly(
                        tuple(1, 2L),
                        tuple(2, 0L),
                        tuple(3, 3L),
                        tuple(4, 0L),
                        tuple(5, 0L),
                        tuple(6, 0L)
                );
        assertThat(response.getReviewQuantity()).isNull();
        verify(userVocabularyRepository, never())
                .countDueReviewVocabs(any(String.class), any(LocalDateTime.class));
    }

    @Test
    void returnsOnlyDueCountForVocabReview() {
        when(userInfoRepository.existsById("user-1")).thenReturn(true);
        List<UserVocabulary> due = List.of(
                mock(UserVocabulary.class),
                mock(UserVocabulary.class),
                mock(UserVocabulary.class)
        );
        when(userVocabularyRepository.findDueReviewVocabs(
                org.mockito.ArgumentMatchers.eq("user-1"),
                any(LocalDateTime.class)
        )).thenReturn(due);
        when(reviewAvailabilityService.findAvailable("user-1", due, "vi"))
                .thenReturn(due.subList(0, 2));

        UserVocabularyInfoResponse response =
                service.getUserVocabularyInfo("user-1", "VOCAB_REVIEW");

        assertThat(response.getInfoType()).isEqualTo(VOCAB_REVIEW);
        assertThat(response.getReviewQuantity()).isEqualTo(2L);
        assertThat(response.getTotalQuantity()).isNull();
        assertThat(response.getQuantityByLevels()).isNull();
        verify(userVocabularyRepository, never()).countUserVocabularyByLevel("user-1");
        verify(userVocabularyRepository, never())
                .countDueReviewVocabs(any(String.class), any(LocalDateTime.class));
    }

    @Test
    void rejectsUnknownInfoType() {
        when(userInfoRepository.existsById("user-1")).thenReturn(true);

        assertThatThrownBy(() -> service.getUserVocabularyInfo("user-1", "UNKNOWN"))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_USER_VOCABULARY_INFO_TYPE));
    }

    @Test
    void rejectsMissingUserBeforeRunningAggregates() {
        when(userInfoRepository.existsById("missing")).thenReturn(false);

        assertThatThrownBy(() -> service.getUserVocabularyInfo("missing", "VOCAB_QUANTITY"))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
        verify(userVocabularyRepository, never()).countUserVocabularyByLevel("missing");
    }

    private UserVocabularyLevelQuantityProjection projection(int level, long quantity) {
        UserVocabularyLevelQuantityProjection projection =
                mock(UserVocabularyLevelQuantityProjection.class);
        when(projection.getLevel()).thenReturn(level);
        when(projection.getQuantity()).thenReturn(quantity);
        return projection;
    }
}
