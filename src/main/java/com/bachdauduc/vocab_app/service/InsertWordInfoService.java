package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.dto.request.wordinfo.InsertWordCategoriesRequest;
import com.bachdauduc.vocab_app.dto.request.wordinfo.InsertWordExampleLocalizationRequest;
import com.bachdauduc.vocab_app.dto.request.wordinfo.InsertWordExampleRequest;
import com.bachdauduc.vocab_app.dto.request.wordinfo.InsertWordIdiomRequest;
import com.bachdauduc.vocab_app.dto.request.wordinfo.InsertWordIdiomTranslationRequest;
import com.bachdauduc.vocab_app.dto.request.wordinfo.InsertWordRelationRequest;
import com.bachdauduc.vocab_app.dto.request.wordinfo.InsertWordSenseLocalizationRequest;
import com.bachdauduc.vocab_app.dto.request.wordinfo.InsertWordSenseRequest;
import com.bachdauduc.vocab_app.dto.request.wordinfo.InsertWordSoundRequest;
import com.bachdauduc.vocab_app.entity.Category;
import com.bachdauduc.vocab_app.entity.WordCategory;
import com.bachdauduc.vocab_app.entity.WordExample;
import com.bachdauduc.vocab_app.entity.WordExampleLocalization;
import com.bachdauduc.vocab_app.entity.WordIdiom;
import com.bachdauduc.vocab_app.entity.WordIdiomTranslation;
import com.bachdauduc.vocab_app.entity.WordRelation;
import com.bachdauduc.vocab_app.entity.WordSense;
import com.bachdauduc.vocab_app.entity.WordSenseLocalization;
import com.bachdauduc.vocab_app.entity.WordSound;
import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import com.bachdauduc.vocab_app.repository.CategoryRepository;
import com.bachdauduc.vocab_app.repository.WordCategoryRepository;
import com.bachdauduc.vocab_app.repository.WordExampleLocalizationRepository;
import com.bachdauduc.vocab_app.repository.WordExampleRepository;
import com.bachdauduc.vocab_app.repository.WordIdiomRepository;
import com.bachdauduc.vocab_app.repository.WordIdiomTranslationRepository;
import com.bachdauduc.vocab_app.repository.WordRelationRepository;
import com.bachdauduc.vocab_app.repository.WordRepository;
import com.bachdauduc.vocab_app.repository.WordSenseLocalizationRepository;
import com.bachdauduc.vocab_app.repository.WordSenseRepository;
import com.bachdauduc.vocab_app.repository.WordSoundRepository;
import com.bachdauduc.vocab_app.utils.RedisUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InsertWordInfoService {
    private static final String SUCCESS_MESSAGE = "Thêm thành công";

    WordRepository wordRepository;
    CategoryRepository categoryRepository;
    WordCategoryRepository wordCategoryRepository;
    WordSenseRepository wordSenseRepository;
    WordSenseLocalizationRepository wordSenseLocalizationRepository;
    WordRelationRepository wordRelationRepository;
    WordExampleRepository wordExampleRepository;
    WordExampleLocalizationRepository wordExampleLocalizationRepository;
    WordIdiomRepository wordIdiomRepository;
    WordIdiomTranslationRepository wordIdiomTranslationRepository;
    WordSoundRepository wordSoundRepository;

    @Transactional
    public String insertWordCategories(InsertWordCategoriesRequest request) {
        log.debug("Start service: method=insertWordCategories, wordId={}, categoryCount={}",
                request.getWordId(), request.getCategoryIds() == null ? 0 : request.getCategoryIds().size());
        assertWordExists(request.getWordId());

        for (String categoryId : request.getCategoryIds()) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseGet(() -> categoryRepository.save(buildMinimalCategory(categoryId)));

            if (!wordCategoryRepository.existsByWordIdAndCategoryId(request.getWordId(), category.getId())) {
                WordCategory wordCategory = new WordCategory();
                wordCategory.setId(newId());
                wordCategory.setWordId(request.getWordId());
                wordCategory.setCategoryId(category.getId());
                wordCategoryRepository.save(wordCategory);
            }
        }

        log.info("Word categories inserted: wordId={}, categoryCount={}",
                request.getWordId(), request.getCategoryIds().size());
        return SUCCESS_MESSAGE;
    }

    @Transactional
    public String insertWordSense(InsertWordSenseRequest request) {
        log.debug("Start service: method=insertWordSense, wordId={}, synonymCount={}, antonymCount={}",
                request.getWordId(),
                request.getSynonyms() == null ? 0 : request.getSynonyms().size(),
                request.getAntonyms() == null ? 0 : request.getAntonyms().size());
        assertWordExists(request.getWordId());

        WordSense wordSense = new WordSense();
        wordSense.setId(newId());
        wordSense.setWordId(request.getWordId());
        wordSense.setDefinition(request.getDefinition());
        wordSense.setSynonyms(toJson(request.getSynonyms()));
        wordSense.setAntonyms(toJson(request.getAntonyms()));
        wordSense.setDerived(toJson(request.getDerived()));
        wordSense.setCoordinateTerms(toJson(request.getCoordinateTerms()));
        wordSense.setFormOf(toJson(request.getFormOf()));
        wordSense.setAltOf(toJson(request.getAltOf()));
        wordSenseRepository.save(wordSense);

        log.info("Word sense inserted: wordId={}, senseId={}", request.getWordId(), wordSense.getId());
        return SUCCESS_MESSAGE;
    }

    @Transactional
    public String insertWordSenseLocalization(InsertWordSenseLocalizationRequest request) {
        log.debug("Start service: method=insertWordSenseLocalization, wordId={}, senseId={}, langCode={}, source={}",
                request.getWordId(), request.getSenseId(), request.getLangCode(), request.getSource());
        assertWordExists(request.getWordId());

        WordSenseLocalization localization = new WordSenseLocalization();
        localization.setId(newId());
        localization.setSenseId(request.getSenseId());
        localization.setWordId(request.getWordId());
        localization.setLangCode(request.getLangCode());
        localization.setShortMeaning(request.getShortMeaning());
        localization.setFullLocalizedDefinition(request.getFullLocalizedDefinition());
        localization.setSource(request.getSource());
        localization.setReviewStatus(defaultReviewStatus(request.getReviewStatus()));
        wordSenseLocalizationRepository.save(localization);

        log.info("Word sense localization inserted: wordId={}, localizationId={}",
                request.getWordId(), localization.getId());
        return SUCCESS_MESSAGE;
    }

    @Transactional
    public String insertWordRelation(InsertWordRelationRequest request) {
        log.debug("Start service: method=insertWordRelation, wordId={}", request.getWordId());
        assertWordExists(request.getWordId());

        WordRelation relation = new WordRelation();
        relation.setId(newId());
        relation.setWordId(request.getWordId());
        relation.setSynonyms(toJson(request.getSynonyms()));
        relation.setAntonyms(toJson(request.getAntonyms()));
        relation.setDerived(toJson(request.getDerived()));
        relation.setCoordinateTerms(toJson(request.getCoordinateTerms()));
        relation.setFormOf(toJson(request.getFormOf()));
        relation.setAltOf(toJson(request.getAltOf()));
        wordRelationRepository.save(relation);

        log.info("Word relation inserted: wordId={}, relationId={}", request.getWordId(), relation.getId());
        return SUCCESS_MESSAGE;
    }

    @Transactional
    public String insertWordExample(InsertWordExampleRequest request) {
        log.debug("Start service: method=insertWordExample, wordId={}, senseId={}, textLength={}",
                request.getWordId(), request.getSenseId(), request.getText() == null ? 0 : request.getText().length());
        assertWordExists(request.getWordId());

        WordExample example = new WordExample();
        example.setId(newId());
        example.setWordId(request.getWordId());
        example.setSenseId(request.getSenseId());
        example.setText(request.getText());
        example.setExampleType(request.getExampleType());
        example.setSourceRef(request.getSourceRef());
        wordExampleRepository.save(example);

        log.info("Word example inserted: wordId={}, exampleId={}", request.getWordId(), example.getId());
        return SUCCESS_MESSAGE;
    }

    @Transactional
    public String insertWordExampleLocalization(InsertWordExampleLocalizationRequest request) {
        log.debug("Start service: method=insertWordExampleLocalization, wordId={}, exampleId={}, senseId={}, langCode={}",
                request.getWordId(), request.getExampleId(), request.getSenseId(), request.getLangCode());
        assertWordExists(request.getWordId());
        assertWordExampleExists(request.getExampleId());

        WordExampleLocalization localization = new WordExampleLocalization();
        localization.setId(newId());
        localization.setExampleId(request.getExampleId());
        localization.setWordId(request.getWordId());
        localization.setSenseId(request.getSenseId());
        localization.setLangCode(request.getLangCode());
        localization.setTranslatedText(request.getTranslatedText());
        localization.setReviewStatus(defaultReviewStatus(request.getReviewStatus()));
        wordExampleLocalizationRepository.save(localization);

        log.info("Word example localization inserted: wordId={}, exampleId={}, localizationId={}",
                request.getWordId(), request.getExampleId(), localization.getId());
        return SUCCESS_MESSAGE;
    }

    @Transactional
    public String insertWordIdiom(InsertWordIdiomRequest request) {
        log.debug("Start service: method=insertWordIdiom, wordId={}, senseId={}, idiom={}",
                request.getWordId(), request.getSenseId(), request.getIdiom());
        assertWordExists(request.getWordId());

        WordIdiom idiom = new WordIdiom();
        idiom.setId(newId());
        idiom.setWordId(request.getWordId());
        idiom.setSenseId(request.getSenseId());
        idiom.setIdiom(request.getIdiom());
        idiom.setDefinition(request.getDefinition());
        idiom.setDefinitionGpt(request.getDefinitionGpt());
        idiom.setExample(request.getExample());
        idiom.setExample2(request.getExample2());
        idiom.setIdiomSource(request.getIdiomSource());
        wordIdiomRepository.save(idiom);

        log.info("Word idiom inserted: wordId={}, idiomId={}", request.getWordId(), idiom.getId());
        return SUCCESS_MESSAGE;
    }

    @Transactional
    public String insertWordIdiomTranslation(InsertWordIdiomTranslationRequest request) {
        log.debug("Start service: method=insertWordIdiomTranslation, idiomId={}, langCode={}",
                request.getIdiomId(), request.getLangCode());
        assertWordIdiomExists(request.getIdiomId());

        WordIdiomTranslation translation = new WordIdiomTranslation();
        translation.setId(newId());
        translation.setIdiomId(request.getIdiomId());
        translation.setIdiom(request.getIdiom());
        translation.setDefinition(request.getDefinition());
        translation.setDefinitionGpt(request.getDefinitionGpt());
        translation.setExample(request.getExample());
        translation.setExample2(request.getExample2());
        translation.setLangCode(request.getLangCode());
        translation.setReviewStatus(defaultReviewStatus(request.getReviewStatus()));
        wordIdiomTranslationRepository.save(translation);

        log.info("Word idiom translation inserted: idiomId={}, translationId={}",
                request.getIdiomId(), translation.getId());
        return SUCCESS_MESSAGE;
    }

    @Transactional
    public String insertWordSound(InsertWordSoundRequest request) {
        log.debug("Start service: method=insertWordSound, wordId={}, soundSource={}, hasMp3={}, hasOgg={}",
                request.getWordId(), request.getSoundSource(), request.getMp3Url() != null, request.getOggUrl() != null);
        assertWordExists(request.getWordId());

        WordSound sound = new WordSound();
        sound.setId(newId());
        sound.setWordId(request.getWordId());
        sound.setIpa(request.getIpa());
        sound.setTags(toJson(request.getTags()));
        sound.setSoundSource(request.getSoundSource());
        sound.setOggUrl(request.getOggUrl());
        sound.setMp3Url(request.getMp3Url());
        sound.setEnpr(request.getEnpr());
        wordSoundRepository.save(sound);

        log.info("Word sound inserted: wordId={}, soundId={}", request.getWordId(), sound.getId());
        return SUCCESS_MESSAGE;
    }

    private void assertWordExists(String wordId) {
        log.debug("Validate word exists: wordId={}", wordId);
        if (!wordRepository.existsById(wordId)) {
            log.warn("Word validation failed: wordId={}, reason=not_found", wordId);
            throw new AppException(ErrorCode.WORD_NOT_FOUND);
        }
    }

    private void assertWordExampleExists(String exampleId) {
        log.debug("Validate word example exists: exampleId={}", exampleId);
        if (!wordExampleRepository.existsById(exampleId)) {
            log.warn("Word example validation failed: exampleId={}, reason=not_found", exampleId);
            throw new AppException(ErrorCode.WORD_EXAMPLE_NOT_FOUND);
        }
    }

    private void assertWordIdiomExists(String idiomId) {
        log.debug("Validate word idiom exists: idiomId={}", idiomId);
        if (!wordIdiomRepository.existsById(idiomId)) {
            log.warn("Word idiom validation failed: idiomId={}, reason=not_found", idiomId);
            throw new AppException(ErrorCode.WORD_IDIOM_NOT_FOUND);
        }
    }

    private Category buildMinimalCategory(String categoryId) {
        Category category = new Category();
        category.setId(categoryId);
        category.setName(categoryId);
        category.setSlug(categoryId);
        return category;
    }

    private String toJson(List<String> values) {
        return values == null ? null : RedisUtil.serialize(values);
    }

    private Integer defaultReviewStatus(Integer reviewStatus) {
        return reviewStatus == null ? 0 : reviewStatus;
    }

    private String newId() {
        return UUID.randomUUID().toString();
    }
}
