package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.dto.response.worddata.WordResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordSenseResponse;
import com.bachdauduc.vocab_app.entity.UserVocabulary;
import com.bachdauduc.vocab_app.entity.WordSenseLocalization;
import com.bachdauduc.vocab_app.repository.UserVocabularyRepository;
import com.bachdauduc.vocab_app.repository.WordSenseLocalizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserVocabularyWordServiceTest {
    @Mock UserVocabularyRepository userVocabularyRepository;
    @Mock WordSenseLocalizationRepository wordSenseLocalizationRepository;
    @Mock GetWordDataService getWordDataService;
    @InjectMocks UserVocabularyService service;

    @Test
    void standardSenseReturnsExistingVietnameseTranslation() {
        savedVocabulary("sense-1", null);
        WordSenseLocalization localization = localization("vi");
        when(wordSenseLocalizationRepository.findFirstBySenseIdAndLangCode("sense-1", "vi"))
                .thenReturn(Optional.of(localization));
        WordSenseResponse translated = sense("sense-1");
        translated.setLocalizationId("localization-1");
        translated.setTrans(WordSenseResponse.Translation.builder()
                .langCode("vi").definition("Qua tao").build());
        WordResponse word = word(translated, sense("sense-2"));
        when(getWordDataService.getWord("word-1", true, "vi"))
                .thenReturn(word);

        WordResponse result = service.getUserVocabWord("saved-1");

        assertThat(result.getSenses()).hasSize(1);
        assertThat(result.getSenses().getFirst().getTrans()).isNotNull();
        assertThat(result.getSenses().getFirst().getTrans().getDefinition()).isEqualTo("Qua tao");
        assertThat(result.getSenses().getFirst().getSenseId()).isEqualTo("sense-1");
        assertThat(word.getSenses()).hasSize(2);
        verify(getWordDataService).getWord("word-1", true, "vi");
        verifyNoMoreInteractions(getWordDataService);
    }

    @Test
    void standardSenseWithoutVietnameseTranslationReturnsEnglish() {
        savedVocabulary("sense-1", null);
        when(wordSenseLocalizationRepository.findFirstBySenseIdAndLangCode("sense-1", "vi"))
                .thenReturn(Optional.empty());
        when(getWordDataService.getWord("word-1", false, null))
                .thenReturn(word(sense("sense-1"), sense("sense-2")));

        WordResponse result = service.getUserVocabWord("saved-1");

        assertThat(result.getSenses()).hasSize(1);
        assertThat(result.getSenses().getFirst().getSenseId()).isEqualTo("sense-1");
        assertThat(result.getSenses().getFirst().getDefinition()).isEqualTo("An apple");
        assertThat(result.getSenses().getFirst().getTrans()).isNull();
        verify(wordSenseLocalizationRepository).findFirstBySenseIdAndLangCode("sense-1", "vi");
        verify(getWordDataService).getWord("word-1", false, null);
        verifyNoMoreInteractions(getWordDataService);
    }

    @Test
    void explicitLocalizationKeepsItsLanguage() {
        savedVocabulary(null, "localization-1");
        when(wordSenseLocalizationRepository.findById("localization-1"))
                .thenReturn(Optional.of(localization("fr")));
        WordSenseResponse localized = sense("sense-1");
        localized.setLocalizationId("localization-1");
        when(getWordDataService.getWord("word-1", true, "fr"))
                .thenReturn(word(localized, sense("sense-2")));

        WordResponse result = service.getUserVocabWord("saved-1");

        assertThat(result.getSenses()).containsExactly(localized);
        verify(wordSenseLocalizationRepository).findById("localization-1");
        verifyNoMoreInteractions(wordSenseLocalizationRepository);
    }

    private void savedVocabulary(String senseId, String localizationId) {
        UserVocabulary vocabulary = new UserVocabulary();
        vocabulary.setId("saved-1");
        vocabulary.setWordId("word-1");
        vocabulary.setSenseId(senseId);
        vocabulary.setSenseLocalizedId(localizationId);
        when(userVocabularyRepository.findById("saved-1")).thenReturn(Optional.of(vocabulary));
    }

    private WordSenseLocalization localization(String language) {
        WordSenseLocalization localization = new WordSenseLocalization();
        localization.setId("localization-1");
        localization.setSenseId("sense-1");
        localization.setWordId("word-1");
        localization.setLangCode(language);
        return localization;
    }

    private WordSenseResponse sense(String id) {
        return WordSenseResponse.builder().senseId(id).definition("An apple").build();
    }

    private WordResponse word(WordSenseResponse... senses) {
        return WordResponse.builder().wordId("word-1").senses(List.of(senses)).build();
    }
}
