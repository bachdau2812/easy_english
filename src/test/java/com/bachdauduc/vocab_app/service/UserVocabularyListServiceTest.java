package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.repository.ListenAndTypeExerciseChallengeRepository;
import com.bachdauduc.vocab_app.repository.UserInfoRepository;
import com.bachdauduc.vocab_app.repository.UserSearchHistoryRepository;
import com.bachdauduc.vocab_app.repository.UserVocabAttemptRepository;
import com.bachdauduc.vocab_app.repository.UserVocabularyRepository;
import com.bachdauduc.vocab_app.repository.WordRepository;
import com.bachdauduc.vocab_app.repository.WordSenseLocalizationRepository;
import com.bachdauduc.vocab_app.repository.WordSenseRepository;
import com.bachdauduc.vocab_app.repository.projection.UserVocabularyExportProjection;
import com.bachdauduc.vocab_app.dto.response.uservocabulary.UserVocabularyExportResponse;
import com.bachdauduc.vocab_app.service.review.ReviewAvailabilityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserVocabularyListServiceTest {
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returnsEveryVocabularyInExcelFieldShapeAcrossBatches() throws Exception {
        when(userInfoRepository.existsById("user-1")).thenReturn(true);

        UserVocabularyExportProjection apple = vocabulary("Apple", "noun", "a fruit", 2,
                "I eat an apple.\r\nAt noon.", "She smiled.");
        UserVocabularyExportProjection book = vocabulary("Book", "noun", null, null, " \t ", null);
        PageRequest firstPage = PageRequest.of(0, 500);
        PageRequest secondPage = PageRequest.of(1, 500);
        when(userVocabularyRepository.findUserVocabularyForExport("user-1", "en", firstPage))
                .thenReturn(new SliceImpl<>(List.of(apple), firstPage, true));
        when(userVocabularyRepository.findUserVocabularyForExport("user-1", "en", secondPage))
                .thenReturn(new SliceImpl<>(List.of(book), secondPage, false));

        List<UserVocabularyExportResponse> result = service.getAllUserVocabularies("user-1", " EN ");

        assertThat(result).hasSize(2);
        JsonNode first = objectMapper.valueToTree(result.get(0));
        List<String> fieldNames = new ArrayList<>();
        first.fieldNames().forEachRemaining(fieldNames::add);
        assertThat(fieldNames)
                .containsExactly("meaning_practice", "word", "pos", "word_sense",
                        "word_practice", "level", "langcode", "examples");
        assertThat(first.path("meaning_practice").asText()).isEmpty();
        assertThat(first.path("word").asText()).isEqualTo("Apple");
        assertThat(first.path("pos").asText()).isEqualTo("noun");
        assertThat(first.path("word_sense").asText()).isEqualTo("a fruit");
        assertThat(first.path("word_practice").asText()).isEmpty();
        assertThat(first.path("level").asInt()).isEqualTo(2);
        assertThat(first.path("langcode").asText()).isEqualTo("en");
        assertThat(first.path("examples").asText())
                .isEqualTo("- I eat an apple. At noon.\n- She smiled.");

        JsonNode second = objectMapper.valueToTree(result.get(1));
        assertThat(second.path("word").asText()).isEqualTo("Book");
        assertThat(second.path("word_sense").asText()).isEmpty();
        assertThat(second.path("level").isNull()).isTrue();
        assertThat(second.path("examples").asText()).isEmpty();
        verify(userVocabularyRepository).findUserVocabularyForExport("user-1", "en", secondPage);
    }

    private UserVocabularyExportProjection vocabulary(
            String word,
            String pos,
            String wordSense,
            Integer level,
            String example1,
            String example2
    ) {
        UserVocabularyExportProjection vocabulary = mock(UserVocabularyExportProjection.class);
        when(vocabulary.getWord()).thenReturn(word);
        when(vocabulary.getPos()).thenReturn(pos);
        when(vocabulary.getWordSense()).thenReturn(wordSense);
        when(vocabulary.getLevel()).thenReturn(level);
        when(vocabulary.getExample1()).thenReturn(example1);
        when(vocabulary.getExample2()).thenReturn(example2);
        return vocabulary;
    }
}
