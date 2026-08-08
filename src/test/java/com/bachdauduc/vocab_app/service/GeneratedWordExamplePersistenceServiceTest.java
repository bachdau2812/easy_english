package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.entity.WordExample;
import com.bachdauduc.vocab_app.entity.WordExampleLocalization;
import com.bachdauduc.vocab_app.repository.WordExampleLocalizationRepository;
import com.bachdauduc.vocab_app.repository.WordExampleRepository;
import com.bachdauduc.vocab_app.service.model.GeneratedWordExample;
import com.bachdauduc.vocab_app.service.review.ReviewVocabCacheRevisionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeneratedWordExamplePersistenceServiceTest {
    @Mock WordExampleRepository wordExampleRepository;
    @Mock WordExampleLocalizationRepository localizationRepository;
    @Mock ReviewVocabCacheRevisionService reviewVocabCacheRevisionService;

    GeneratedWordExamplePersistenceService service;

    @BeforeEach
    void setUp() {
        service = new GeneratedWordExamplePersistenceService(
                wordExampleRepository,
                localizationRepository,
                reviewVocabCacheRevisionService
        );
    }

    @Test
    void persistAddsOnlyTheDeficitAndSavesMatchingVietnameseRows() {
        when(wordExampleRepository.findByWordIdAndSenseId("word-1", "sense-1"))
                .thenReturn(List.of(existing("Existing example one."), existing("Existing example two.")));

        service.persist(List.of(
                generated("New bank example one.", "Ban dich mot."),
                generated("New bank example two.", "Ban dich hai."),
                generated("New bank example three.", "Ban dich ba.")
        ));

        ArgumentCaptor<WordExample> exampleCaptor = ArgumentCaptor.forClass(WordExample.class);
        ArgumentCaptor<WordExampleLocalization> localizationCaptor =
                ArgumentCaptor.forClass(WordExampleLocalization.class);
        verify(wordExampleRepository, times(2)).save(exampleCaptor.capture());
        verify(localizationRepository, times(2)).save(localizationCaptor.capture());

        assertThat(exampleCaptor.getAllValues()).allSatisfy(example -> {
            assertThat(example.getWordId()).isEqualTo("word-1");
            assertThat(example.getSenseId()).isEqualTo("sense-1");
            assertThat(example.getExampleType()).isEqualTo("AI_GENERATED");
            assertThat(example.getSourceRef()).isEqualTo("GROQ:openai/gpt-oss-120b");
        });
        assertThat(localizationCaptor.getAllValues()).allSatisfy(localization -> {
            assertThat(localization.getWordId()).isEqualTo("word-1");
            assertThat(localization.getSenseId()).isEqualTo("sense-1");
            assertThat(localization.getLangCode()).isEqualTo("vi");
            assertThat(localization.getReviewStatus()).isEqualTo(1);
        });
        assertThat(localizationCaptor.getAllValues())
                .extracting(WordExampleLocalization::getExampleId)
                .containsExactlyElementsOf(exampleCaptor.getAllValues().stream().map(WordExample::getId).toList());
    }

    @Test
    void persistDoesNothingWhenTheSenseAlreadyHasFourDistinctExamples() {
        when(wordExampleRepository.findByWordIdAndSenseId("word-1", "sense-1"))
                .thenReturn(List.of(existing("One."), existing("Two."), existing("Three."), existing("Four.")));

        service.persist(List.of(generated("A fifth bank example.", "Ban dich thu nam.")));

        verify(wordExampleRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(localizationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private WordExample existing(String text) {
        WordExample example = new WordExample();
        example.setWordId("word-1");
        example.setSenseId("sense-1");
        example.setText(text);
        return example;
    }

    private GeneratedWordExample generated(String text, String translation) {
        return new GeneratedWordExample("request-1", "word-1", "sense-1", text, translation);
    }
}
