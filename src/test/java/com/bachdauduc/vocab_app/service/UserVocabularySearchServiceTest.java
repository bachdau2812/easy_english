package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.dto.response.uservocabulary.UserVocabularySearchResponse;
import com.bachdauduc.vocab_app.dto.response.uservocabulary.UserVocabularyAutocompleteResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordSenseResponse;
import com.bachdauduc.vocab_app.entity.WordSenseLocalization;
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
import com.bachdauduc.vocab_app.repository.projection.UserVocabularyProjection;
import com.bachdauduc.vocab_app.repository.projection.UserVocabularyAutocompleteProjection;
import com.bachdauduc.vocab_app.service.review.ReviewAvailabilityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserVocabularySearchServiceTest {
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

    @Test
    void exactSearchNormalizesTextAndReturnsOnlySavedStandardSense() {
        when(userInfoRepository.existsById("user-1")).thenReturn(true);
        UserVocabularyProjection projection = projection(
                "saved-1", "word-1", "Apple", "sense-1", null
        );
        when(userVocabularyRepository.findUserVocabByNormalizedWord(
                eq("user-1"), eq("apple"), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(projection)));

        WordResponse word = word(
                "word-1",
                sense("sense-1", null),
                sense("sense-2", null)
        );
        when(getWordDataService.getWord("word-1", false, null)).thenReturn(word);

        Page<UserVocabularySearchResponse> result =
                service.searchUserVocabulary("user-1", " APPLE ", 0, 20);

        assertThat(result.getContent()).hasSize(1);
        UserVocabularySearchResponse item = result.getContent().getFirst();
        assertThat(item.getUserVocabulary().getId()).isEqualTo("saved-1");
        assertThat(item.getUserVocabulary().getWord()).isEqualTo("Apple");
        assertThat(item.getWord().getSenses())
                .extracting(WordSenseResponse::getSenseId)
                .containsExactly("sense-1");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userVocabularyRepository).findUserVocabByNormalizedWord(
                eq("user-1"), eq("apple"), pageable.capture()
        );
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void autocompleteReturnsOnlyDropdownDataWithoutLoadingWordDetails() {
        when(userInfoRepository.existsById("user-1")).thenReturn(true);
        UserVocabularyAutocompleteProjection match = mock(UserVocabularyAutocompleteProjection.class);
        when(match.getUserVocabId()).thenReturn("saved-1");
        when(match.getWord()).thenReturn("Apple");
        when(match.getLevel()).thenReturn(2);
        when(match.getPos()).thenReturn("noun");
        when(userVocabularyRepository.findUserVocabByNormalizedWordPrefix(
                eq("user-1"), eq("app"), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(match)));

        Page<UserVocabularyAutocompleteResponse> result =
                service.autocompleteUserVocabulary("user-1", " App ", 1, 5);

        assertThat(result.getContent()).containsExactly(UserVocabularyAutocompleteResponse.builder()
                .userVocabId("saved-1").word("Apple").level(2).pos("noun").build());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userVocabularyRepository).findUserVocabByNormalizedWordPrefix(
                eq("user-1"), eq("app"), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        verifyNoInteractions(getWordDataService, wordSenseLocalizationRepository, wordSenseRepository);
        verify(userVocabularyRepository, never()).findUserVocabByNormalizedWord(
                any(), any(), any(Pageable.class)
        );
    }

    @Test
    void standardSavedSenseSearchReturnsExistingVietnameseTranslation() {
        when(userInfoRepository.existsById("user-1")).thenReturn(true);
        UserVocabularyProjection saved = projection("saved-1", "word-1", "Apple", "sense-1", null);
        when(userVocabularyRepository.findUserVocabByNormalizedWord(
                eq("user-1"), eq("apple"), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(saved)));
        WordSenseLocalization localization = new WordSenseLocalization();
        localization.setLangCode("vi");
        when(wordSenseLocalizationRepository.findFirstBySenseIdAndLangCode("sense-1", "vi"))
                .thenReturn(Optional.of(localization));
        WordSenseResponse translated = sense("sense-1", "localization-1");
        translated.setTrans(WordSenseResponse.Translation.builder()
                .langCode("vi").definition("Qua tao").build());
        when(getWordDataService.getWord("word-1", true, "vi"))
                .thenReturn(word("word-1", translated, sense("sense-2", null)));

        UserVocabularySearchResponse result = service
                .searchUserVocabulary("user-1", "apple", 0, 20).getContent().getFirst();

        assertThat(result.getWord().getSenses()).containsExactly(translated);
        assertThat(result.getWord().getSenses().getFirst().getTrans().getDefinition()).isEqualTo("Qua tao");
    }

    @Test
    void localizedSavedSenseUsesLocalizationLanguageAndKeepsLocalizedSense() {
        when(userInfoRepository.existsById("user-1")).thenReturn(true);
        UserVocabularyProjection projection = projection(
                "saved-localized", "word-1", "Apple", null, "localization-1"
        );
        when(userVocabularyRepository.findUserVocabByNormalizedWord(
                eq("user-1"), eq("apple"), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(projection)));

        WordSenseLocalization localization = new WordSenseLocalization();
        localization.setId("localization-1");
        localization.setSenseId("sense-1");
        localization.setWordId("word-1");
        localization.setLangCode("vi");
        when(wordSenseLocalizationRepository.findById("localization-1"))
                .thenReturn(Optional.of(localization));

        WordResponse word = word(
                "word-1",
                sense("sense-1", "localization-1"),
                sense("sense-2", "localization-2")
        );
        when(getWordDataService.getWord("word-1", true, "vi")).thenReturn(word);

        UserVocabularySearchResponse item =
                service.searchUserVocabulary("user-1", "apple", 0, 20)
                        .getContent()
                        .getFirst();

        assertThat(item.getWord().getSenses())
                .extracting(WordSenseResponse::getLocalizationId)
                .containsExactly("localization-1");
    }

    @Test
    void blankTextReturnsEmptyPageAfterValidatingUser() {
        when(userInfoRepository.existsById("user-1")).thenReturn(true);

        Page<UserVocabularyAutocompleteResponse> result =
                service.autocompleteUserVocabulary("user-1", "   ", -1, 0);

        assertThat(result).isEmpty();
        assertThat(result.getPageable().getPageNumber()).isZero();
        assertThat(result.getPageable().getPageSize()).isEqualTo(1);
        verify(userVocabularyRepository, never()).findUserVocabByNormalizedWordPrefix(
                any(), any(), any(Pageable.class)
        );
        verify(getWordDataService, never()).getWord(any(), any(Boolean.class), any());
    }

    @Test
    void missingUserIsRejectedBeforeRepositorySearch() {
        when(userInfoRepository.existsById("missing")).thenReturn(false);

        assertThatThrownBy(() ->
                service.searchUserVocabulary("missing", "apple", 0, 20)
        ).isInstanceOfSatisfying(AppException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));

        verify(userVocabularyRepository, never()).findUserVocabByNormalizedWord(
                any(), any(), any(Pageable.class)
        );
    }

    @Test
    void autocompleteRejectsMissingUserBeforeQueryingSavedWords() {
        assertThatThrownBy(() -> service.autocompleteUserVocabulary("missing", "app", 0, 20))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
        verifyNoInteractions(userVocabularyRepository, getWordDataService);
    }

    private UserVocabularyProjection projection(
            String id,
            String wordId,
            String word,
            String senseId,
            String senseLocalizedId
    ) {
        UserVocabularyProjection projection = mock(UserVocabularyProjection.class);
        when(projection.getId()).thenReturn(id);
        when(projection.getUserId()).thenReturn("user-1");
        when(projection.getWordId()).thenReturn(wordId);
        when(projection.getWord()).thenReturn(word);
        when(projection.getSenseId()).thenReturn(senseId);
        when(projection.getSenseLocalizedId()).thenReturn(senseLocalizedId);
        when(projection.getLevel()).thenReturn(1);
        when(projection.getCurrentLevelCorrectTurns()).thenReturn(0);
        return projection;
    }

    private WordResponse word(String wordId, WordSenseResponse... senses) {
        return WordResponse.builder()
                .wordId(wordId)
                .word("Apple")
                .senses(List.of(senses))
                .build();
    }

    private WordSenseResponse sense(String senseId, String localizationId) {
        return WordSenseResponse.builder()
                .senseId(senseId)
                .localizationId(localizationId)
                .build();
    }
}
