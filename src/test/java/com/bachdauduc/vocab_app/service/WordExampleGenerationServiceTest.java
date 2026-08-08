package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.entity.UserVocabulary;
import com.bachdauduc.vocab_app.entity.Word;
import com.bachdauduc.vocab_app.entity.WordExample;
import com.bachdauduc.vocab_app.entity.WordSense;
import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import com.bachdauduc.vocab_app.repository.WordExampleRepository;
import com.bachdauduc.vocab_app.repository.WordRepository;
import com.bachdauduc.vocab_app.repository.WordSenseRepository;
import com.bachdauduc.vocab_app.service.abstraction.WordExampleGenerator;
import com.bachdauduc.vocab_app.service.model.GeneratedWordExample;
import com.bachdauduc.vocab_app.service.model.WordExampleGenerationInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WordExampleGenerationServiceTest {
    @Mock WordRepository wordRepository;
    @Mock WordSenseRepository wordSenseRepository;
    @Mock WordExampleRepository wordExampleRepository;
    @Mock WordExampleGenerator generator;
    @Mock GeneratedWordExamplePersistenceService persistenceService;

    WordExampleGenerationService service;

    @BeforeEach
    void setUp() {
        service = new WordExampleGenerationService(
                wordRepository, wordSenseRepository, wordExampleRepository, generator, persistenceService);
    }

    @Test
    void ensureExamplesBatchesOnlyDeficitsForDistinctStandardSenses() {
        List<UserVocabulary> vocabularies = List.of(
                vocab("uv-1", "word-1", "sense-1", null),
                vocab("uv-2", "word-1", "sense-1", null),
                vocab("uv-3", "word-2", "sense-2", null),
                vocab("uv-4", "word-3", "sense-3", null),
                vocab("uv-5", "word-4", null, "localized-4")
        );
        when(wordRepository.findAllById(any())).thenReturn(List.of(
                word("word-1", "bank", "noun", "B1"),
                word("word-2", "charge", "verb", "B2"),
                word("word-3", "plain", "adjective", null)
        ));
        when(wordSenseRepository.findAllById(any())).thenReturn(List.of(
                sense("sense-1", "word-1", "the land alongside a river"),
                sense("sense-2", "word-2", "to ask someone to pay money"),
                sense("sense-3", "word-3", "simple and not decorated")
        ));
        when(wordExampleRepository.findBySenseIdIn(any())).thenReturn(List.of(
                example("word-2", "sense-2", "They charge a small fee."),
                example("word-2", "sense-2", "The hotel will charge for breakfast."),
                example("word-3", "sense-3", "She wore a plain shirt."),
                example("word-3", "sense-3", "The room looked plain."),
                example("word-3", "sense-3", "He chose a plain design."),
                example("word-3", "sense-3", "The box was plain and practical.")
        ));
        List<GeneratedWordExample> generated = List.of(new GeneratedWordExample(
                "request", "word-1", "sense-1", "They sat on the bank.", "Ho ngoi tren bo."));
        when(generator.generate(any())).thenReturn(generated);

        service.ensureExamples(vocabularies);

        ArgumentCaptor<List<WordExampleGenerationInput>> captor = ArgumentCaptor.forClass(List.class);
        verify(generator).generate(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue())
                .extracting(WordExampleGenerationInput::wordId, WordExampleGenerationInput::requiredExampleCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("word-1", 4),
                        org.assertj.core.groups.Tuple.tuple("word-2", 2)
                );
        verify(persistenceService).persist(generated);
    }

    @Test
    void ensureExamplesKeepsReviewAvailableWhenGroqFails() {
        when(wordRepository.findAllById(any())).thenReturn(List.of(word("word-1", "bank", "noun", "B1")));
        when(wordSenseRepository.findAllById(any())).thenReturn(List.of(
                sense("sense-1", "word-1", "the land alongside a river")));
        when(wordExampleRepository.findBySenseIdIn(any())).thenReturn(List.of());
        when(generator.generate(any())).thenThrow(new AppException(ErrorCode.WORD_EXAMPLE_GENERATION_FAILED));

        assertThatCode(() -> service.ensureExamples(List.of(vocab("uv-1", "word-1", "sense-1", null))))
                .doesNotThrowAnyException();

        verify(persistenceService, never()).persist(any());
    }

    private UserVocabulary vocab(String id, String wordId, String senseId, String localizedId) {
        UserVocabulary vocabulary = new UserVocabulary();
        vocabulary.setId(id);
        vocabulary.setWordId(wordId);
        vocabulary.setSenseId(senseId);
        vocabulary.setSenseLocalizedId(localizedId);
        return vocabulary;
    }

    private Word word(String id, String text, String pos, String level) {
        Word word = new Word();
        word.setId(id);
        word.setWord(text);
        word.setPos(pos);
        word.setCertLevel(level);
        return word;
    }

    private WordSense sense(String id, String wordId, String definition) {
        WordSense sense = new WordSense();
        sense.setId(id);
        sense.setWordId(wordId);
        sense.setDefinition(definition);
        return sense;
    }

    private WordExample example(String wordId, String senseId, String text) {
        WordExample example = new WordExample();
        example.setWordId(wordId);
        example.setSenseId(senseId);
        example.setText(text);
        return example;
    }
}
