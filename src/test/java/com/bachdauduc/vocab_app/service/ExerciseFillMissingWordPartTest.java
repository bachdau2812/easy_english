package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.entity.UserVocabulary;
import com.bachdauduc.vocab_app.entity.Word;
import com.bachdauduc.vocab_app.entity.WordSense;
import com.bachdauduc.vocab_app.repository.UserVocabularyRepository;
import com.bachdauduc.vocab_app.repository.WordExampleRepository;
import com.bachdauduc.vocab_app.repository.WordRepository;
import com.bachdauduc.vocab_app.repository.WordSenseLocalizationRepository;
import com.bachdauduc.vocab_app.repository.WordSenseRepository;
import com.bachdauduc.vocab_app.repository.WordSoundRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExerciseFillMissingWordPartTest {
    @Mock UserVocabularyRepository userVocabularyRepository;
    @Mock WordRepository wordRepository;
    @Mock WordSenseRepository wordSenseRepository;
    @Mock WordSenseLocalizationRepository wordSenseLocalizationRepository;
    @Mock WordSoundRepository wordSoundRepository;
    @Mock WordExampleRepository wordExampleRepository;
    @InjectMocks ExerciseService service;

    @ParameterizedTest
    @CsvSource(value = {
            "1, river, 3, 4", "2, river, 3, 4", "3, river, 3, 4",
            "4, river, 4, 4", "5, river, 4, 4", "6, river, 4, 4",
            "1, elephant, 3, 4", "2, elephant, 3, 4", "3, elephant, 3, 4",
            "4, elephant, 4, 7", "5, elephant, 4, 7", "6, elephant, 4, 7",
            "null, river, 3, 4", "1, look over, 3, 4", "4, look over, 4, 7"
    }, nullValues = "null")
    void directGeneratorUsesUpdatedMaskQuota(Integer level, String text, int minimum, int maximum) {
        UserVocabulary target = new UserVocabulary();
        target.setId("vocab-1");
        target.setWordId("word-1");
        target.setSenseId("sense-1");
        target.setLevel(level);
        Word word = new Word();
        word.setId("word-1");
        word.setWord(text);
        WordSense sense = new WordSense();
        sense.setId("sense-1");
        sense.setDefinition("meaning");
        when(userVocabularyRepository.findAllById(List.of("vocab-1"))).thenReturn(List.of(target));
        when(wordRepository.findById("word-1")).thenReturn(Optional.of(word));
        when(wordSenseRepository.findById("sense-1")).thenReturn(Optional.of(sense));

        for (int attempt = 0; attempt < 30; attempt++) {
            var quiz = service.generateFillMissingWordPartQuiz("vocab-1", List.of("vocab-1"), "vi");
            assertThat(quiz.getMetadata().size()).isBetween(minimum, maximum);
            assertThat(quiz.getMaskedWord()).startsWith(text.substring(0, 1));
            assertThat(quiz.getCorrectAnswer()).isEqualTo(text);
        }
    }
}
