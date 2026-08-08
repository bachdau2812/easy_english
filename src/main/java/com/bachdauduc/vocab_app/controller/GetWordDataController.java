package com.bachdauduc.vocab_app.controller;

import com.bachdauduc.vocab_app.dto.response.ApiResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.BasicWordSearchResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordExampleResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordFormResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordIdiomResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordRelationResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordSenseResponse;
import com.bachdauduc.vocab_app.entity.Category;
import com.bachdauduc.vocab_app.entity.Word;
import com.bachdauduc.vocab_app.service.GetWordDataService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/word-data")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetWordDataController {
    GetWordDataService getWordDataService;

    @GetMapping("/examples")
    public ApiResponse<List<WordExampleResponse>> getWordExamples(
            @RequestParam String wordId,
            @RequestParam(defaultValue = "false") boolean isTrans,
            @RequestParam(required = false) String transLangCode
    ) {
        log.info("Request received: action=getWordExamples, wordId={}, isTrans={}", wordId, isTrans);
        return success(getWordDataService.getWordExamples(wordId, isTrans, transLangCode));
    }

    @GetMapping("/idioms")
    public ApiResponse<List<WordIdiomResponse>> getWordIdioms(
            @RequestParam String wordId,
            @RequestParam(defaultValue = "false") boolean isTrans,
            @RequestParam(required = false) String transLangCode
    ) {
        log.info("Request received: action=getWordIdioms, wordId={}, isTrans={}", wordId, isTrans);
        return success(getWordDataService.getWordIdioms(wordId, isTrans, transLangCode));
    }

    @GetMapping("/forms")
    public ApiResponse<List<WordFormResponse>> getWordForms(@RequestParam String wordId) {
        log.info("Request received: action=getWordForms, wordId={}", wordId);
        return success(getWordDataService.getWordForms(wordId));
    }

    @GetMapping("/relations")
    public ApiResponse<List<WordRelationResponse>> getWordRelations(@RequestParam String wordId) {
        log.info("Request received: action=getWordRelations, wordId={}", wordId);
        return success(getWordDataService.getWordRelations(wordId));
    }

    @GetMapping("/senses")
    public ApiResponse<List<WordSenseResponse>> getWordSenses(
            @RequestParam String wordId,
            @RequestParam(defaultValue = "false") boolean isTrans,
            @RequestParam(required = false) String transLangCode
    ) {
        log.info("Request received: action=getWordSenses, wordId={}, isTrans={}", wordId, isTrans);
        return success(getWordDataService.getWordSenses(wordId, isTrans, transLangCode));
    }

    @GetMapping("/word")
    public ApiResponse<WordResponse> getWord(
            @RequestParam String wordId,
            @RequestParam(defaultValue = "false") boolean isTrans,
            @RequestParam(required = false) String transLangCode,
            @RequestParam(required = false) String userId
    ) {
        log.info("Request received: action=getWord, wordId={}, isTrans={}, userId={}", wordId, isTrans, userId);
        return success(getWordDataService.getWord(wordId, isTrans, transLangCode, userId));
    }

    @GetMapping("/words/search")
    public ApiResponse<List<WordResponse>> searchWordsByText(
            @RequestParam String text,
            @RequestParam(defaultValue = "false") boolean isTrans,
            @RequestParam(required = false) String transLangCode
    ) {
        log.info("Request received: action=searchWordsByText, text={}, isTrans={}", text, isTrans);
        return success(getWordDataService.searchWordsByText(text, isTrans, transLangCode));
    }

    @GetMapping({"/words/basic-search", "/word/basic-search"})
    public ApiResponse<List<BasicWordSearchResponse>> searchWordObjectsByText(
            @RequestParam String text,
            @RequestParam(defaultValue = "false") boolean isAutocomplete,
            @RequestParam(defaultValue = "false") boolean isUniqueSearch
    ) {
        log.info("Request received: action=searchWordObjectsByText, text={}, isAutocomplete={}, isUniqueSearch={}",
                text, isAutocomplete, isUniqueSearch);
        return success(getWordDataService.searchWordObjectsByText(text, isAutocomplete, isUniqueSearch));
    }

    @GetMapping("/words/basic-search/by-category")
    public ApiResponse<Page<Word>> searchWordObjectsByTextAndCategory(
            @RequestParam String text,
            @RequestParam String categoryId,
            @RequestParam(defaultValue = "false") boolean isAutocomplete,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        log.info("Request received: action=searchWordObjectsByTextAndCategory, text={}, categoryId={}, isAutocomplete={}, page={}, limit={}",
                text, categoryId, isAutocomplete, page, limit);
        return success(getWordDataService.searchWordObjectsByTextAndCategory(text, categoryId, isAutocomplete, page, limit));
    }

    @GetMapping("/words/basic-search/by-level")
    public ApiResponse<Page<Word>> searchWordObjectsByTextAndLevel(
            @RequestParam String text,
            @RequestParam String level,
            @RequestParam(defaultValue = "false") boolean isAutocomplete,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        log.info("Request received: action=searchWordObjectsByTextAndLevel, text={}, level={}, isAutocomplete={}, page={}, limit={}",
                text, level, isAutocomplete, page, limit);
        return success(getWordDataService.searchWordObjectsByTextAndLevel(text, level, isAutocomplete, page, limit));
    }

    @GetMapping("/words/by-category")
    public ApiResponse<Page<Word>> getWordsByCategory(
            @RequestParam String categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        log.info("Request received: action=getWordsByCategory, categoryId={}, page={}, limit={}",
                categoryId, page, limit);
        return success(getWordDataService.getWordsByCategory(categoryId, page, limit));
    }

    @GetMapping("/words/by-level")
    public ApiResponse<Page<Word>> getWordsByLevel(
            @RequestParam String level,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        log.info("Request received: action=getWordsByLevel, level={}, page={}, limit={}",
                level, page, limit);
        return success(getWordDataService.getWordsByLevel(level, page, limit));
    }

    @GetMapping("/categories")
    public ApiResponse<List<Category>> getVocabularyCategories() {
        log.info("Request received: action=getVocabularyCategories");
        return success(getWordDataService.getVocabularyCategories());
    }

    private <T> ApiResponse<T> success(T result) {
        return ApiResponse.<T>builder()
                .message("Get word data successfully")
                .traceId(MDC.get("traceId"))
                .result(result)
                .build();
    }
}
