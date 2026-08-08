package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.dto.response.worddata.BasicWordSearchResponse;
import com.bachdauduc.vocab_app.entity.Word;
import com.bachdauduc.vocab_app.properties.RedisKeyProperties;
import com.bachdauduc.vocab_app.repository.CategoryRepository;
import com.bachdauduc.vocab_app.repository.UserInfoRepository;
import com.bachdauduc.vocab_app.repository.UserSearchHistoryRepository;
import com.bachdauduc.vocab_app.repository.WordCategoryRepository;
import com.bachdauduc.vocab_app.repository.WordExampleLocalizationRepository;
import com.bachdauduc.vocab_app.repository.WordExampleRepository;
import com.bachdauduc.vocab_app.repository.WordFormRepository;
import com.bachdauduc.vocab_app.repository.WordIdiomRepository;
import com.bachdauduc.vocab_app.repository.WordRelationRepository;
import com.bachdauduc.vocab_app.repository.WordRepository;
import com.bachdauduc.vocab_app.repository.WordSenseLocalizationRepository;
import com.bachdauduc.vocab_app.repository.WordSenseRepository;
import com.bachdauduc.vocab_app.repository.WordSoundRepository;
import com.bachdauduc.vocab_app.service.implementation.AzureTranslator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetWordDataServiceTest {
    @Mock WordRepository wordRepository;
    @Mock WordExampleRepository wordExampleRepository;
    @Mock WordIdiomRepository wordIdiomRepository;
    @Mock WordFormRepository wordFormRepository;
    @Mock WordRelationRepository wordRelationRepository;
    @Mock WordSenseRepository wordSenseRepository;
    @Mock WordSenseLocalizationRepository wordSenseLocalizationRepository;
    @Mock WordExampleLocalizationRepository wordExampleLocalizationRepository;
    @Mock WordSoundRepository wordSoundRepository;
    @Mock WordCategoryRepository wordCategoryRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock UserInfoRepository userInfoRepository;
    @Mock UserSearchHistoryRepository userSearchHistoryRepository;
    @Mock AzureTranslator azureTranslator;
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock RedisKeyProperties redisKeyProperties;

    GetWordDataService service;

    @BeforeEach
    void setUp() {
        service = new GetWordDataService(
                wordRepository,
                wordExampleRepository,
                wordIdiomRepository,
                wordFormRepository,
                wordRelationRepository,
                wordSenseRepository,
                wordSenseLocalizationRepository,
                wordExampleLocalizationRepository,
                wordSoundRepository,
                wordCategoryRepository,
                categoryRepository,
                userInfoRepository,
                userSearchHistoryRepository,
                azureTranslator,
                redisTemplate,
                redisKeyProperties
        );
    }

    @Test
    void searchWordObjectsByTextReturnsUniqueWordsByPrefixWhenUniqueSearchIsTrue() {
        when(wordRepository.findUniqueWordsByNormalizedWordPrefix("app"))
                .thenReturn(List.of("apple", "application"));

        List<BasicWordSearchResponse> response = service.searchWordObjectsByText(" App ", false, true);

        assertThat(response)
                .extracting(BasicWordSearchResponse::getWord)
                .containsExactly("apple", "application");
        assertThat(response)
                .extracting(BasicWordSearchResponse::getPos)
                .containsOnlyNulls();
        verify(wordRepository).findUniqueWordsByNormalizedWordPrefix("app");
    }

    @Test
    void searchWordObjectsByTextKeepsExistingExactSearchWhenUniqueSearchIsFalse() {
        Word apple = word("word-1", "apple", "noun");
        Word duplicate = word("word-2", "apple", "noun");
        Word verb = word("word-3", "apple", "verb");
        when(wordRepository.findByNormalizedWord("apple"))
                .thenReturn(List.of(apple, duplicate, verb));

        List<BasicWordSearchResponse> response = service.searchWordObjectsByText("apple", false, false);

        assertThat(response)
                .extracting(BasicWordSearchResponse::getId)
                .containsExactly("word-1", "word-3");
        assertThat(response.getFirst().getWord()).isEqualTo("apple");
        assertThat(response.getFirst().getPos()).isEqualTo("noun");
    }

    @Test
    void searchWordObjectsByTextDeduplicatesByTupleAndKeepsDifferentWordCase() {
        Word capitalized = word("word-1", "Apple", "noun");
        Word sameCapitalizedTuple = word("word-2", "Apple", "noun");
        Word lowercase = word("word-3", "apple", "noun");
        Word sameLowercaseTuple = word("word-4", "apple", "noun");
        Word differentPos = word("word-5", "apple", "verb");
        when(wordRepository.findByNormalizedWordPrefix("app"))
                .thenReturn(List.of(
                        capitalized,
                        sameCapitalizedTuple,
                        lowercase,
                        sameLowercaseTuple,
                        differentPos
                ));

        List<BasicWordSearchResponse> response = service.searchWordObjectsByText("app", true, false);

        assertThat(response)
                .extracting(BasicWordSearchResponse::getId)
                .containsExactly("word-1", "word-3", "word-5");
    }

    private Word word(String id, String text, String pos) {
        Word word = new Word();
        word.setId(id);
        word.setWord(text);
        word.setNormalizedWord(text.toLowerCase());
        word.setPos(pos);
        word.setLang("English");
        word.setLangCode("en");
        word.setWordSource("LOCAL");
        word.setCertLevel("B1");
        return word;
    }
}
