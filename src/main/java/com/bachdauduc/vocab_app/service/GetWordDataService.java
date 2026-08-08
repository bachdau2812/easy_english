package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.dto.response.worddata.BasicWordSearchResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordExampleResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordFormResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordIdiomResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordRelationResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordSenseResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordSoundResponse;
import com.bachdauduc.vocab_app.entity.Category;
import com.bachdauduc.vocab_app.entity.UserSearchHistory;
import com.bachdauduc.vocab_app.entity.Word;
import com.bachdauduc.vocab_app.entity.WordCategory;
import com.bachdauduc.vocab_app.entity.WordExampleLocalization;
import com.bachdauduc.vocab_app.entity.WordSenseLocalization;
import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
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
import com.bachdauduc.vocab_app.repository.projection.WordExampleProjection;
import com.bachdauduc.vocab_app.repository.projection.WordFormProjection;
import com.bachdauduc.vocab_app.repository.projection.WordIdiomProjection;
import com.bachdauduc.vocab_app.repository.projection.WordRelationProjection;
import com.bachdauduc.vocab_app.repository.projection.WordSenseProjection;
import com.bachdauduc.vocab_app.repository.projection.WordSoundProjection;
import com.bachdauduc.vocab_app.service.implementation.AzureTranslator;
import com.bachdauduc.vocab_app.utils.RedisUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetWordDataService {
    private static final String SOURCE_MOCHI = "MOCHI";
    private static final String SOURCE_AZURE = "AZURE";

    WordRepository wordRepository;
    WordExampleRepository wordExampleRepository;
    WordIdiomRepository wordIdiomRepository;
    WordFormRepository wordFormRepository;
    WordRelationRepository wordRelationRepository;
    WordSenseRepository wordSenseRepository;
    WordSenseLocalizationRepository wordSenseLocalizationRepository;
    WordExampleLocalizationRepository wordExampleLocalizationRepository;
    WordSoundRepository wordSoundRepository;
    WordCategoryRepository wordCategoryRepository;
    CategoryRepository categoryRepository;
    UserInfoRepository userInfoRepository;
    UserSearchHistoryRepository userSearchHistoryRepository;
    AzureTranslator azureTranslator;
    RedisTemplate<String, String> redisTemplate;
    RedisKeyProperties redisKeyProperties;

    @Transactional
    public List<WordExampleResponse> getWordExamples(String wordId, boolean isTrans, String transLangCode) {
        log.debug("Start service: method=getWordExamples, wordId={}, isTrans={}, transLangCode={}",
                wordId, isTrans, transLangCode);
        Word word = getRequiredWord(wordId);
        List<WordExampleResponse> examples = loadWordExamples(word, isTrans, transLangCode);
        log.info("Word examples loaded: wordId={}, exampleCount={}", wordId, examples.size());
        return examples;
    }

    public List<WordIdiomResponse> getWordIdioms(String wordId, boolean isTrans, String transLangCode) {
        log.debug("Start service: method=getWordIdioms, wordId={}, isTrans={}, transLangCode={}",
                wordId, isTrans, transLangCode);
        getRequiredWord(wordId);
        List<WordIdiomResponse> idioms = loadWordIdioms(wordId, isTrans, transLangCode);
        log.info("Word idioms loaded: wordId={}, idiomCount={}", wordId, idioms.size());
        return idioms;
    }

    public List<WordFormResponse> getWordForms(String wordId) {
        log.debug("Start service: method=getWordForms, wordId={}", wordId);
        getRequiredWord(wordId);
        List<WordFormResponse> forms = loadWordForms(wordId);
        log.info("Word forms loaded: wordId={}, formCount={}", wordId, forms.size());
        return forms;
    }

    public List<WordRelationResponse> getWordRelations(String wordId) {
        log.debug("Start service: method=getWordRelations, wordId={}", wordId);
        getRequiredWord(wordId);
        List<WordRelationResponse> relations = loadWordRelations(wordId);
        log.info("Word relations loaded: wordId={}, relationCount={}", wordId, relations.size());
        return relations;
    }

    @Transactional
    public List<WordSenseResponse> getWordSenses(String wordId, boolean isTrans, String transLangCode) {
        log.debug("Start service: method=getWordSenses, wordId={}, isTrans={}, transLangCode={}",
                wordId, isTrans, transLangCode);
        Word word = getRequiredWord(wordId);
        List<WordSenseResponse> senses = loadWordSenses(word, isTrans, transLangCode);
        log.info("Word senses loaded: wordId={}, senseCount={}, otherSource={}",
                wordId, senses.size(), word.getOtherSource());
        return senses;
    }

    @Transactional
    public WordResponse getWord(String wordId, boolean isTrans, String transLangCode) {
        return getWord(wordId, isTrans, transLangCode, null);
    }

    @Transactional
    public WordResponse getWord(String wordId, boolean isTrans, String transLangCode, String userId) {
        log.info("Start service: method=getWord, wordId={}, isTrans={}, transLangCode={}",
                wordId, isTrans, transLangCode);
        WordResponse response = getWordResponseWithCache(wordId, isTrans, transLangCode);
        recordUserSearchHistory(userId, wordId);
        return response;
    }

    private WordResponse buildWordResponse(String wordId, boolean isTrans, String transLangCode) {
        Word word = getRequiredWord(wordId);
        List<WordExampleResponse> examples = loadWordExamples(word, isTrans, transLangCode);
        List<WordSenseResponse> senses = loadWordSensesWithExamples(word, isTrans, transLangCode, examples);
        List<String> categories = getCategories(wordId);
        List<WordSoundResponse> sounds = loadWordSounds(word);
        List<WordIdiomResponse> idioms = loadWordIdioms(wordId, isTrans, transLangCode);
        List<WordFormResponse> forms = loadWordForms(wordId);
        List<WordRelationResponse> relations = loadWordRelations(wordId);

        WordResponse response = WordResponse.builder()
                .wordId(word.getId())
                .word(word.getWord())
                .normalizedWord(word.getNormalizedWord())
                .pos(word.getPos())
                .certLevel(word.getCertLevel())
                .lang(word.getLang())
                .langCode(word.getLangCode())
                .wordSource(word.getWordSource())
                .otherSource(word.getOtherSource())
                .categories(categories)
                .sounds(sounds)
                .senses(senses)
                .idioms(idioms)
                .forms(forms)
                .relation(relations.stream().findFirst().orElse(null))
                .build();
        log.info("Word data response built: wordId={}, otherSource={}, categoryCount={}, soundCount={}, senseCount={}, exampleCount={}, idiomCount={}, formCount={}, relationCount={}",
                wordId, word.getOtherSource(), categories.size(), sounds.size(), senses.size(), examples.size(),
                idioms.size(), forms.size(), relations.size());
        return response;
    }

    @Transactional
    public List<WordResponse> searchWordsByText(String text, boolean isTrans, String transLangCode) {
        log.info("Start service: method=searchWordsByText, text={}, isTrans={}, transLangCode={}",
                text, isTrans, transLangCode);
        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        String trimmedText = text.trim();
        List<Word> matchedWords = findMatchedWordsByText(trimmedText);

        List<WordResponse> responses = matchedWords.stream()
                .map(word -> getWordResponseWithCache(word.getId(), isTrans, transLangCode))
                .toList();
        log.info("Words searched by text: text={}, isTrans={}, matchedCount={}, responseCount={}",
                trimmedText, isTrans, matchedWords.size(), responses.size());
        return responses;
    }

    public List<BasicWordSearchResponse> searchWordObjectsByText(String text, boolean isAutocomplete) {
        return searchWordObjectsByText(text, isAutocomplete, false);
    }

    public List<BasicWordSearchResponse> searchWordObjectsByText(
            String text,
            boolean isAutocomplete,
            boolean isUniqueSearch
    ) {
        log.info("Start service: method=searchWordObjectsByText, text={}, isAutocomplete={}, isUniqueSearch={}",
                text, isAutocomplete, isUniqueSearch);
        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        String normalizedText = normalizeSearchText(text);
        if (isUniqueSearch) {
            List<BasicWordSearchResponse> uniqueWords = wordRepository.findUniqueWordsByNormalizedWordPrefix(normalizedText)
                    .stream()
                    .map(this::toUniqueBasicWordSearchResponse)
                    .toList();
            log.info("Unique word objects searched by prefix: text={}, resultCount={}",
                    normalizedText, uniqueWords.size());
            return uniqueWords;
        }

        List<Word> words = isAutocomplete
                ? distinctWordsBySearchTuple(wordRepository.findByNormalizedWordPrefix(normalizedText))
                : findMatchedWordsByText(normalizedText);
        List<BasicWordSearchResponse> responses = words.stream()
                .map(this::toBasicWordSearchResponse)
                .toList();
        log.info("Word objects searched by text: text={}, isAutocomplete={}, resultCount={}",
                normalizedText, isAutocomplete, responses.size());
        return responses;
    }

    public Page<Word> getWordsByCategory(String categoryId, int page, int limit) {
        log.info("Start service: method=getWordsByCategory, categoryId={}, page={}, limit={}",
                categoryId, page, limit);
        if (!categoryRepository.existsById(categoryId)) {
            log.warn("Get words by category failed: categoryId={}, reason=category_not_found", categoryId);
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        Page<Word> words = wordRepository.findWordsByCategoryId(categoryId, pageRequest(page, limit));
        log.info("Words by category loaded: categoryId={}, resultCount={}, totalElements={}",
                categoryId, words.getNumberOfElements(), words.getTotalElements());
        return words;
    }

    public Page<Word> getWordsByLevel(String level, int page, int limit) {
        log.info("Start service: method=getWordsByLevel, level={}, page={}, limit={}",
                level, page, limit);
        String certLevel = level.trim();
        Page<Word> words = wordRepository.findByCertLevel(certLevel, pageRequest(page, limit));
        log.info("Words by level loaded: level={}, resultCount={}, totalElements={}",
                certLevel, words.getNumberOfElements(), words.getTotalElements());
        return words;
    }

    public Page<Word> searchWordObjectsByTextAndCategory(
            String text,
            String categoryId,
            boolean isAutocomplete,
            int page,
            int limit
    ) {
        log.info("Start service: method=searchWordObjectsByTextAndCategory, text={}, categoryId={}, isAutocomplete={}, page={}, limit={}",
                text, categoryId, isAutocomplete, page, limit);
        if (!StringUtils.hasText(text)) {
            return Page.empty(pageRequest(page, limit));
        }
        if (!categoryRepository.existsById(categoryId)) {
            log.warn("Search word objects by category failed: categoryId={}, reason=category_not_found", categoryId);
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        String normalizedText = normalizeSearchText(text);
        Page<Word> words = isAutocomplete
                ? wordRepository.findWordsByCategoryIdAndNormalizedWordPrefix(
                categoryId,
                normalizedText,
                pageRequest(page, limit)
        )
                : wordRepository.findWordsByCategoryIdAndNormalizedWord(
                categoryId,
                normalizedText,
                pageRequest(page, limit)
        );
        log.info("Word objects searched by text and category: normalizedText={}, categoryId={}, isAutocomplete={}, resultCount={}, totalElements={}",
                normalizedText, categoryId, isAutocomplete, words.getNumberOfElements(), words.getTotalElements());
        return words;
    }

    public Page<Word> searchWordObjectsByTextAndLevel(
            String text,
            String level,
            boolean isAutocomplete,
            int page,
            int limit
    ) {
        log.info("Start service: method=searchWordObjectsByTextAndLevel, text={}, level={}, isAutocomplete={}, page={}, limit={}",
                text, level, isAutocomplete, page, limit);
        if (!StringUtils.hasText(text)) {
            return Page.empty(pageRequest(page, limit));
        }

        String normalizedText = normalizeSearchText(text);
        String certLevel = level.trim().toUpperCase();
        Page<Word> words = isAutocomplete
                ? wordRepository.findWordsByCertLevelAndNormalizedWordPrefix(
                certLevel,
                normalizedText,
                pageRequest(page, limit)
        )
                : wordRepository.findWordsByCertLevelAndNormalizedWord(
                certLevel,
                normalizedText,
                pageRequest(page, limit)
        );
        log.info("Word objects searched by text and level: normalizedText={}, level={}, isAutocomplete={}, resultCount={}, totalElements={}",
                normalizedText, certLevel, isAutocomplete, words.getNumberOfElements(), words.getTotalElements());
        return words;
    }

    public List<Category> getVocabularyCategories() {
        log.info("Start service: method=getVocabularyCategories");
        List<Category> categories = categoryRepository.findAllByOrderByNameAsc();
        log.info("Vocabulary categories loaded: categoryCount={}", categories.size());
        return categories;
    }

    private BasicWordSearchResponse toBasicWordSearchResponse(Word word) {
        return BasicWordSearchResponse.builder()
                .id(word.getId())
                .word(word.getWord())
                .normalizedWord(word.getNormalizedWord())
                .pos(word.getPos())
                .lang(word.getLang())
                .langCode(word.getLangCode())
                .wordSource(word.getWordSource())
                .otherSource(word.getOtherSource())
                .certLevel(word.getCertLevel())
                .createdAt(word.getCreatedAt())
                .updatedAt(word.getUpdatedAt())
                .build();
    }

    private BasicWordSearchResponse toUniqueBasicWordSearchResponse(String word) {
        return BasicWordSearchResponse.builder()
                .word(word)
                .build();
    }

    private WordResponse getWordResponseWithCache(String wordId, boolean isTrans, String transLangCode) {
        String cacheKey = wordCacheKey(wordId, isTrans);
        log.info("Word cache lookup: wordId={}, isTrans={}, transLangCode={}, key={}",
                wordId, isTrans, transLangCode, cacheKey);
        String cachedWord = redisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cachedWord)) {
            WordResponse cachedResponse = RedisUtil.deserialize(cachedWord, WordResponse.class);
            if (cachedResponse != null) {
                log.info("Word cache hit: wordId={}, isTrans={}, key={}",
                        wordId, isTrans, cacheKey);
                return cachedResponse;
            }
            log.warn("Word response redis cache invalid: wordId={}, key={}", wordId, cacheKey);
        }

        log.info("Word cache miss: wordId={}, isTrans={}, key={}", wordId, isTrans, cacheKey);
        WordResponse response = buildWordResponse(wordId, isTrans, transLangCode);
        redisTemplate.opsForValue().set(cacheKey, RedisUtil.serialize(response), Duration.ofHours(5));
        log.info("Word response loaded from DB and cached: wordId={}, isTrans={}, key={}, ttlHours={}",
                wordId, isTrans, cacheKey, 5);
        return response;
    }

    private String wordCacheKey(String wordId, boolean isTrans) {
        return isTrans
                ? redisKeyProperties.wordWithTransKey(wordId)
                : redisKeyProperties.wordWithoutTransKey(wordId);
    }

    private List<Word> findMatchedWordsByText(String text) {
        return distinctWordsBySearchTuple(wordRepository.findByNormalizedWord(normalizeSearchText(text)));
    }

    private List<Word> distinctWordsBySearchTuple(List<Word> words) {
        return words.stream()
                .collect(Collectors.toMap(
                        this::toSearchTuple,
                        Function.identity(),
                        (first, duplicate) -> first,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();
    }

    private SearchTuple toSearchTuple(Word word) {
        return new SearchTuple(word.getWord(), word.getPos(), word.getWordSource(), word.getCertLevel());
    }

    private record SearchTuple(String word, String pos, String source, String level) {
    }

    private String normalizeSearchText(String text) {
        return text.trim().toLowerCase();
    }

    private void recordUserSearchHistory(String userId, String wordId) {
        if (!StringUtils.hasText(userId)) {
            log.debug("Skip user search history: reason=missing_user_id, wordId={}", wordId);
            return;
        }
        if (!userInfoRepository.existsById(userId)) {
            log.warn("Record user search history failed: userId={}, reason=user_not_found", userId);
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        if (!wordRepository.existsById(wordId)) {
            log.warn("Record user search history failed: wordId={}, reason=word_not_found", wordId);
            throw new AppException(ErrorCode.WORD_NOT_FOUND);
        }

        if (userSearchHistoryRepository.existsByUserIdAndWordId(userId, wordId)) {
            int updated = userSearchHistoryRepository.refreshSearchHistory(userId, wordId);
            log.info("User search history refreshed: userId={}, wordId={}, updatedRows={}", userId, wordId, updated);
            return;
        }

        UserSearchHistory history = new UserSearchHistory();
        history.setId(UUID.randomUUID().toString());
        history.setUserId(userId);
        history.setWordId(wordId);
        userSearchHistoryRepository.save(history);
        log.info("User search history inserted from getWord: userId={}, wordId={}", userId, wordId);
    }

    private List<WordSenseResponse> loadWordSensesWithExamples(
            Word word,
            boolean isTrans,
            String transLangCode,
            List<WordExampleResponse> examples
    ) {
        boolean isMochi = SOURCE_MOCHI.equalsIgnoreCase(word.getOtherSource());
        Map<String, List<WordExampleResponse>> examplesBySense = examples.stream()
                .filter(example -> StringUtils.hasText(resolveExampleSenseKey(example, isMochi)))
                .collect(Collectors.groupingBy(
                        example -> resolveExampleSenseKey(example, isMochi),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<WordSenseResponse> senses = loadWordSenses(word, isTrans, transLangCode).stream()
                .peek(sense -> sense.setExamples(examplesBySense.getOrDefault(
                        resolveSenseKey(sense, isMochi),
                        List.of()
                )))
                .toList();
        log.debug("Examples grouped into senses: wordId={}, isMochi={}, exampleGroupCount={}, senseCount={}",
                word.getId(), isMochi, examplesBySense.size(), senses.size());
        return senses;
    }

    private List<WordExampleResponse> loadWordExamples(Word word, boolean isTrans, String transLangCode) {
        List<WordExampleProjection> projections;
        if (SOURCE_MOCHI.equalsIgnoreCase(word.getOtherSource())) {
            projections = isTrans && StringUtils.hasText(transLangCode)
                    ? wordExampleRepository.findMochiWordExamples(word.getId(), transLangCode)
                    : wordExampleRepository.findMochiWordExamplesWithoutTrans(word.getId());
            log.debug("Load word examples using MOCHI path: wordId={}, isTrans={}, langCode={}, projectionCount={}",
                    word.getId(), isTrans, transLangCode, projections.size());
        } else {
            if (isTrans && StringUtils.hasText(transLangCode)) {
                projections = wordExampleRepository.findWordExamplesWithTrans(word.getId(), transLangCode);
                Map<String, WordExampleLocalization> azureLocalizationsByExampleId =
                        saveMissingExampleAzureTranslations(word.getId(), transLangCode, projections);
                log.debug("Load word examples using standard path: wordId={}, isTrans={}, langCode={}, projectionCount={}, savedTranslationCount={}",
                        word.getId(), true, transLangCode, projections.size(), azureLocalizationsByExampleId.size());
                return projections.stream()
                        .map(projection -> toWordExampleResponse(
                                projection,
                                azureLocalizationsByExampleId.get(projection.getWordExampleId())
                        ))
                        .toList();
            }

            projections = wordExampleRepository.findWordExamples(word.getId());
            log.debug("Load word examples using standard path: wordId={}, isTrans={}, langCode={}, projectionCount={}",
                    word.getId(), isTrans, transLangCode, projections.size());
        }

        return projections.stream()
                .map(this::toWordExampleResponse)
                .toList();
    }

    private List<WordIdiomResponse> loadWordIdioms(String wordId, boolean isTrans, String transLangCode) {
        List<WordIdiomProjection> projections = isTrans && StringUtils.hasText(transLangCode)
                ? wordIdiomRepository.findWordIdiomsWithTrans(wordId, transLangCode)
                : wordIdiomRepository.findWordIdioms(wordId);
        log.debug("Load word idioms: wordId={}, isTrans={}, langCode={}, projectionCount={}",
                wordId, isTrans, transLangCode, projections.size());

        return projections.stream()
                .map(this::toWordIdiomResponse)
                .toList();
    }

    private List<WordFormResponse> loadWordForms(String wordId) {
        List<WordFormProjection> projections = wordFormRepository.findWordForms(wordId);
        log.debug("Load word forms: wordId={}, projectionCount={}", wordId, projections.size());
        return projections.stream()
                .map(this::toWordFormResponse)
                .toList();
    }

    private List<WordRelationResponse> loadWordRelations(String wordId) {
        List<WordRelationProjection> projections = wordRelationRepository.findWordRelations(wordId);
        log.debug("Load word relations: wordId={}, projectionCount={}", wordId, projections.size());
        return projections.stream()
                .map(this::toWordRelationResponse)
                .toList();
    }

    private List<WordSenseResponse> loadWordSenses(Word word, boolean isTrans, String transLangCode) {
        if (SOURCE_MOCHI.equalsIgnoreCase(word.getOtherSource())) {
            String mochiLangCode = isTrans && StringUtils.hasText(transLangCode) ? transLangCode : null;
            List<WordSenseProjection> mochiProjections =
                    wordSenseRepository.findMochiWordSenseLocalizations(word.getId(), mochiLangCode);
            log.debug("Load word senses using MOCHI localization path: wordId={}, langCode={}, projectionCount={}",
                    word.getId(), mochiLangCode, mochiProjections.size());
            return mochiProjections.stream()
                    .map(projection -> toWordSenseResponse(projection, null))
                    .toList();
        }

        if (!isTrans || !StringUtils.hasText(transLangCode)) {
            List<WordSenseProjection> projections = wordSenseRepository.findWordSenses(word.getId());
            log.debug("Load word senses without translation: wordId={}, projectionCount={}",
                    word.getId(), projections.size());
            return projections.stream()
                    .map(projection -> toWordSenseResponse(projection, null))
                    .toList();
        }

        List<WordSenseProjection> projections = wordSenseRepository.findWordSensesWithTrans(word.getId(), transLangCode);
        log.debug("Load word senses with translation: wordId={}, langCode={}, projectionCount={}",
                word.getId(), transLangCode, projections.size());
        Map<String, WordSenseLocalization> azureLocalizationsBySenseId =
                saveMissingAzureTranslations(word.getId(), transLangCode, projections);

        return projections.stream()
                .map(projection -> toWordSenseResponse(projection, azureLocalizationsBySenseId.get(projection.getSenseId())))
                .toList();
    }

    private List<WordSoundResponse> loadWordSounds(Word word) {
        List<WordSoundProjection> projections = SOURCE_MOCHI.equalsIgnoreCase(word.getOtherSource())
                ? wordSoundRepository.findWordSoundsBySource(word.getId(), SOURCE_MOCHI)
                : wordSoundRepository.findWordSounds(word.getId());
        log.debug("Load word sounds: wordId={}, otherSource={}, projectionCount={}",
                word.getId(), word.getOtherSource(), projections.size());

        return projections.stream()
                .map(this::toWordSoundResponse)
                .toList();
    }

    private Map<String, WordSenseLocalization> saveMissingAzureTranslations(
            String wordId,
            String transLangCode,
            List<WordSenseProjection> projections
    ) {
        List<WordSenseProjection> missingTranslationProjections = projections.stream()
                .filter(projection -> StringUtils.hasText(projection.getSenseId()))
                .filter(projection -> StringUtils.hasText(projection.getDefinition()))
                .filter(projection -> !StringUtils.hasText(projection.getTransDefinition()))
                .toList();

        if (missingTranslationProjections.isEmpty()) {
            log.debug("No missing Azure translations: wordId={}, langCode={}, projectionCount={}",
                    wordId, transLangCode, projections.size());
            return Map.of();
        }

        log.info("Missing sense translations detected: wordId={}, langCode={}, missingCount={}",
                wordId, transLangCode, missingTranslationProjections.size());
        List<String> definitions = missingTranslationProjections.stream()
                .map(WordSenseProjection::getDefinition)
                .toList();
        Map<String, String> translatedByDefinition = azureTranslator.translate(definitions, transLangCode);

        Map<String, WordSenseLocalization> savedBySenseId = new LinkedHashMap<>();
        for (WordSenseProjection projection : missingTranslationProjections) {
            String translatedDefinition = translatedByDefinition.get(projection.getDefinition());
            if (!StringUtils.hasText(translatedDefinition)) {
                continue;
            }

            WordSenseLocalization localization = new WordSenseLocalization();
            localization.setId(UUID.randomUUID().toString());
            localization.setSenseId(projection.getSenseId());
            localization.setWordId(wordId);
            localization.setLangCode(transLangCode);
            localization.setShortMeaning(null);
            localization.setFullLocalizedDefinition(translatedDefinition);
            localization.setSource(SOURCE_AZURE);
            localization.setReviewStatus(0);
            wordSenseLocalizationRepository.save(localization);
            savedBySenseId.put(projection.getSenseId(), localization);
        }

        log.info("Azure translations saved: wordId={}, langCode={}, savedCount={}",
                wordId, transLangCode, savedBySenseId.size());
        return savedBySenseId;
    }

    private Map<String, WordExampleLocalization> saveMissingExampleAzureTranslations(
            String wordId,
            String transLangCode,
            List<WordExampleProjection> projections
    ) {
        List<WordExampleProjection> missingTranslationProjections = projections.stream()
                .filter(projection -> StringUtils.hasText(projection.getWordExampleId()))
                .filter(projection -> StringUtils.hasText(projection.getSenseId()))
                .filter(projection -> StringUtils.hasText(projection.getSentence()))
                .filter(projection -> !StringUtils.hasText(projection.getTrans()))
                .toList();

        if (missingTranslationProjections.isEmpty()) {
            log.debug("No missing example Azure translations: wordId={}, langCode={}, projectionCount={}",
                    wordId, transLangCode, projections.size());
            return Map.of();
        }

        log.info("Missing example translations detected: wordId={}, langCode={}, missingCount={}",
                wordId, transLangCode, missingTranslationProjections.size());
        List<String> sentences = missingTranslationProjections.stream()
                .map(WordExampleProjection::getSentence)
                .distinct()
                .toList();
        Map<String, String> translatedBySentence = azureTranslator.translate(sentences, transLangCode);

        Map<String, WordExampleLocalization> savedByExampleId = new LinkedHashMap<>();
        for (WordExampleProjection projection : missingTranslationProjections) {
            String translatedSentence = translatedBySentence.get(projection.getSentence());
            if (!StringUtils.hasText(translatedSentence)) {
                continue;
            }

            WordExampleLocalization localization = new WordExampleLocalization();
            localization.setId(UUID.randomUUID().toString());
            localization.setExampleId(projection.getWordExampleId());
            localization.setWordId(wordId);
            localization.setSenseId(projection.getSenseId());
            localization.setLangCode(transLangCode);
            localization.setTranslatedText(translatedSentence);
            localization.setReviewStatus(0);
            wordExampleLocalizationRepository.save(localization);
            savedByExampleId.put(projection.getWordExampleId(), localization);
        }

        log.info("Azure example translations saved: wordId={}, langCode={}, savedCount={}",
                wordId, transLangCode, savedByExampleId.size());
        return savedByExampleId;
    }

    private List<String> getCategories(String wordId) {
        List<String> categoryIds = wordCategoryRepository.findByWordId(wordId).stream()
                .map(WordCategory::getCategoryId)
                .toList();
        if (categoryIds.isEmpty()) {
            log.debug("No categories mapped to word: wordId={}", wordId);
            return List.of();
        }

        List<String> categories = categoryRepository.findByIdIn(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity(), (first, second) -> first))
                .entrySet()
                .stream()
                .filter(entry -> categoryIds.contains(entry.getKey()))
                .map(entry -> entry.getValue().getName())
                .toList();
        log.debug("Categories loaded for word: wordId={}, categoryIdCount={}, categoryNameCount={}",
                wordId, categoryIds.size(), categories.size());
        return categories;
    }

    private WordExampleResponse toWordExampleResponse(WordExampleProjection projection) {
        return toWordExampleResponse(projection, null);
    }

    private WordExampleResponse toWordExampleResponse(
            WordExampleProjection projection,
            WordExampleLocalization savedAzureLocalization
    ) {
        return WordExampleResponse.builder()
                .wordExampleId(projection.getWordExampleId())
                .senseId(projection.getSenseId())
                .wordSenseLocalizationId(projection.getWordSenseLocalizationId())
                .wordId(projection.getWordId())
                .word(projection.getWord())
                .pos(projection.getPos())
                .certLevel(projection.getCertLevel())
                .sentence(projection.getSentence())
                .trans(savedAzureLocalization != null
                        ? savedAzureLocalization.getTranslatedText()
                        : projection.getTrans())
                .build();
    }

    private WordIdiomResponse toWordIdiomResponse(WordIdiomProjection projection) {
        return WordIdiomResponse.builder()
                .wordId(projection.getWordId())
                .word(projection.getWord())
                .pos(projection.getPos())
                .certLevel(projection.getCertLevel())
                .idiom(projection.getIdiom())
                .definition(projection.getDefinition())
                .example(projection.getExample())
                .example2(projection.getExample2())
                .trans(hasIdiomTranslation(projection) ? WordIdiomResponse.Translation.builder()
                        .idiom(projection.getTransIdiom())
                        .definition(projection.getTransDefinition())
                        .example(projection.getTransExample())
                        .example2(projection.getTransExample2())
                        .build() : null)
                .build();
    }

    private WordFormResponse toWordFormResponse(WordFormProjection projection) {
        return WordFormResponse.builder()
                .wordId(projection.getWordId())
                .word(projection.getWord())
                .pos(projection.getPos())
                .certLevel(projection.getCertLevel())
                .form(projection.getForm())
                .tags(toStringList(projection.getTags()))
                .build();
    }

    private WordRelationResponse toWordRelationResponse(WordRelationProjection projection) {
        return WordRelationResponse.builder()
                .wordId(projection.getWordId())
                .word(projection.getWord())
                .pos(projection.getPos())
                .certLevel(projection.getCertLevel())
                .synonyms(toStringList(projection.getSynonyms()))
                .antonyms(toStringList(projection.getAntonyms()))
                .derived(toStringList(projection.getDerived()))
                .coordinateTerms(toStringList(projection.getCoordinateTerms()))
                .formOf(toStringList(projection.getFormOf()))
                .altOf(toStringList(projection.getAltOf()))
                .build();
    }

    private WordSenseResponse toWordSenseResponse(
            WordSenseProjection projection,
            WordSenseLocalization savedAzureLocalization
    ) {
        return WordSenseResponse.builder()
                .senseId(projection.getSenseId())
                .localizationId(projection.getLocalizationId())
                .wordId(projection.getWordId())
                .word(projection.getWord())
                .pos(projection.getPos())
                .certLevel(projection.getCertLevel())
                .shortMeaning(resolveShortMeaning(projection, savedAzureLocalization))
                .definition(projection.getDefinition())
                .synonyms(toStringList(projection.getSynonyms()))
                .antonyms(toStringList(projection.getAntonyms()))
                .examples(List.of())
                .trans(toSenseTranslation(projection, savedAzureLocalization))
                .derived(toStringList(projection.getDerived()))
                .coordinateTerms(toStringList(projection.getCoordinateTerms()))
                .formOf(toStringList(projection.getFormOf()))
                .altOf(toStringList(projection.getAltOf()))
                .build();
    }

    private WordSoundResponse toWordSoundResponse(WordSoundProjection projection) {
        return WordSoundResponse.builder()
                .wordId(projection.getWordId())
                .ipa(projection.getIpa())
                .tags(toStringList(projection.getTags()))
                .soundSource(projection.getSoundSource())
                .oggUrl(projection.getOggUrl())
                .mp3Url(projection.getMp3Url())
                .enpr(projection.getEnpr())
                .build();
    }

    private WordSenseResponse.Translation toSenseTranslation(
            WordSenseProjection projection,
            WordSenseLocalization savedAzureLocalization
    ) {
        if (savedAzureLocalization != null) {
            return WordSenseResponse.Translation.builder()
                    .langCode(savedAzureLocalization.getLangCode())
                    .shortMeaning(savedAzureLocalization.getShortMeaning())
                    .definition(savedAzureLocalization.getFullLocalizedDefinition())
                    .build();
        }

        if (!StringUtils.hasText(projection.getTransDefinition())
                && !StringUtils.hasText(projection.getTransShortMeaning())) {
            return null;
        }

        return WordSenseResponse.Translation.builder()
                .langCode(projection.getTransLangCode())
                .shortMeaning(projection.getTransShortMeaning())
                .definition(projection.getTransDefinition())
                .build();
    }

    private String resolveShortMeaning(
            WordSenseProjection projection,
            WordSenseLocalization savedAzureLocalization
    ) {
        return savedAzureLocalization != null
                ? savedAzureLocalization.getShortMeaning()
                : projection.getShortMeaning();
    }

    private boolean hasIdiomTranslation(WordIdiomProjection projection) {
        return StringUtils.hasText(projection.getTransIdiom())
                || StringUtils.hasText(projection.getTransDefinition())
                || StringUtils.hasText(projection.getTransExample())
                || StringUtils.hasText(projection.getTransExample2());
    }

    private String resolveSenseKey(WordSenseResponse sense, boolean isMochi) {
        return isMochi ? sense.getLocalizationId() : sense.getSenseId();
    }

    private String resolveExampleSenseKey(WordExampleResponse example, boolean isMochi) {
        return isMochi ? example.getWordSenseLocalizationId() : example.getSenseId();
    }

    private Word getRequiredWord(String wordId) {
        return wordRepository.findById(wordId)
                .orElseThrow(() -> new AppException(ErrorCode.WORD_NOT_FOUND));
    }

    private PageRequest pageRequest(int page, int limit) {
        return PageRequest.of(Math.max(page, 0), Math.max(limit, 1));
    }

    private List<String> toStringList(String json) {
        return RedisUtil.deserializeList(json, String.class);
    }
}
