package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.constant.ExerciseType;
import com.bachdauduc.vocab_app.dto.response.worddata.WordSoundResponse;
import com.bachdauduc.vocab_app.entity.UserVocabulary;
import com.bachdauduc.vocab_app.repository.UserVocabularyRepository;
import com.bachdauduc.vocab_app.service.review.ReviewExample;
import com.bachdauduc.vocab_app.service.review.ReviewQuizFactory;
import com.bachdauduc.vocab_app.service.review.ReviewVocabDataLoader;
import com.bachdauduc.vocab_app.service.review.ReviewVocabSnapshot;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExerciseSentenceGeneratorTest {
    @Mock UserVocabularyRepository repository;
    @Mock ReviewVocabDataLoader loader;
    @InjectMocks ExerciseService service;
    @ParameterizedTest
    @EnumSource(value = ExerciseType.class, names = {"VOCAB_SENTENCE_TO_MEANING", "VOCAB_SENTENCE_BLANK_TO_SOUND",
            "VOCAB_CHOOSE_WORD_IN_SENTENCE_BLANK", "VOCAB_FILL_WORD_IN_SENTENCE_BLANK"})
    void legacyGeneratorsUseTheSharedSentencePipeline(ExerciseType type) {
        ReflectionTestUtils.setField(service, "userVocabularyRepository", repository);
        ReflectionTestUtils.setField(service, "reviewVocabDataLoader", loader);
        ReflectionTestUtils.setField(service, "reviewQuizFactory", new ReviewQuizFactory());
        List<UserVocabulary> vocabularies = new ArrayList<>();
        var snapshots = new LinkedHashMap<String, ReviewVocabSnapshot>();
        for (String word : List.of("wrap up", "forest", "river", "ocean")) {
            var vocabulary = new UserVocabulary();
            vocabulary.setId(word);
            vocabulary.setWordId(word);
            vocabulary.setLevel(3);
            vocabularies.add(vocabulary);
            snapshots.put(word, new ReviewVocabSnapshot(ReviewVocabSnapshot.CURRENT_SCHEMA_VERSION,
                    word, "sense", "vi", word, "verb", "meaning " + word, null,
                    List.of(WordSoundResponse.builder().mp3Url("https://audio/" + word).build()),
                    List.of(new ReviewExample("example", "Please wrap it up.", "translation")), Instant.now()));
        }
        var ids = vocabularies.stream().map(UserVocabulary::getId).toList();
        when(repository.findAllById(ids)).thenReturn(vocabularies);
        when(loader.load(vocabularies, "vi")).thenReturn(snapshots);
        var quiz = switch (type) {
            case VOCAB_SENTENCE_TO_MEANING -> service.generateSentenceToMeaningQuiz("wrap up", ids, "vi");
            case VOCAB_SENTENCE_BLANK_TO_SOUND -> service.generateSentenceBlankToSoundQuiz("wrap up", ids, "vi");
            case VOCAB_CHOOSE_WORD_IN_SENTENCE_BLANK -> service.generateChooseWordInSentenceBlankQuiz("wrap up", ids, "vi");
            case VOCAB_FILL_WORD_IN_SENTENCE_BLANK -> service.generateFillWordInSentenceBlankQuiz("wrap up", ids, "vi");
            default -> throw new IllegalArgumentException();
        };
        assertThat(quiz.getExerciseType()).isEqualTo(type);
        assertThat(quiz.getMaskedWord()).isNotBlank();
        assertThat(quiz.getSentence()).contains(" it ");
        assertThat(quiz.getTargetSpans()).extracting(span -> span.text()).containsExactly("wrap", "up");
    }
}
