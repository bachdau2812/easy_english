package com.bachdauduc.vocab_app.controller;

import com.bachdauduc.vocab_app.dto.request.wordinfo.InsertWordCategoriesRequest;
import com.bachdauduc.vocab_app.dto.request.wordinfo.InsertWordExampleLocalizationRequest;
import com.bachdauduc.vocab_app.dto.request.wordinfo.InsertWordExampleRequest;
import com.bachdauduc.vocab_app.dto.request.wordinfo.InsertWordIdiomRequest;
import com.bachdauduc.vocab_app.dto.request.wordinfo.InsertWordIdiomTranslationRequest;
import com.bachdauduc.vocab_app.dto.request.wordinfo.InsertWordRelationRequest;
import com.bachdauduc.vocab_app.dto.request.wordinfo.InsertWordSenseLocalizationRequest;
import com.bachdauduc.vocab_app.dto.request.wordinfo.InsertWordSenseRequest;
import com.bachdauduc.vocab_app.dto.request.wordinfo.InsertWordSoundRequest;
import com.bachdauduc.vocab_app.dto.response.ApiResponse;
import com.bachdauduc.vocab_app.service.InsertWordInfoService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/word-info")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InsertWordInfoController {
    InsertWordInfoService insertWordInfoService;

    @PostMapping("/categories")
    public ApiResponse<String> insertWordCategories(@Valid @RequestBody InsertWordCategoriesRequest request) {
        log.info("Request received: action=insertWordCategories, wordId={}", request.getWordId());
        return success(insertWordInfoService.insertWordCategories(request));
    }

    @PostMapping("/senses")
    public ApiResponse<String> insertWordSense(@Valid @RequestBody InsertWordSenseRequest request) {
        log.info("Request received: action=insertWordSense, wordId={}", request.getWordId());
        return success(insertWordInfoService.insertWordSense(request));
    }

    @PostMapping("/sense-localizations")
    public ApiResponse<String> insertWordSenseLocalization(@Valid @RequestBody InsertWordSenseLocalizationRequest request) {
        log.info("Request received: action=insertWordSenseLocalization, wordId={}", request.getWordId());
        return success(insertWordInfoService.insertWordSenseLocalization(request));
    }

    @PostMapping("/relations")
    public ApiResponse<String> insertWordRelation(@Valid @RequestBody InsertWordRelationRequest request) {
        log.info("Request received: action=insertWordRelation, wordId={}", request.getWordId());
        return success(insertWordInfoService.insertWordRelation(request));
    }

    @PostMapping("/examples")
    public ApiResponse<String> insertWordExample(@Valid @RequestBody InsertWordExampleRequest request) {
        log.info("Request received: action=insertWordExample, wordId={}", request.getWordId());
        return success(insertWordInfoService.insertWordExample(request));
    }

    @PostMapping("/example-localizations")
    public ApiResponse<String> insertWordExampleLocalization(@Valid @RequestBody InsertWordExampleLocalizationRequest request) {
        log.info("Request received: action=insertWordExampleLocalization, exampleId={}", request.getExampleId());
        return success(insertWordInfoService.insertWordExampleLocalization(request));
    }

    @PostMapping("/idioms")
    public ApiResponse<String> insertWordIdiom(@Valid @RequestBody InsertWordIdiomRequest request) {
        log.info("Request received: action=insertWordIdiom, wordId={}", request.getWordId());
        return success(insertWordInfoService.insertWordIdiom(request));
    }

    @PostMapping("/idiom-translations")
    public ApiResponse<String> insertWordIdiomTranslation(@Valid @RequestBody InsertWordIdiomTranslationRequest request) {
        log.info("Request received: action=insertWordIdiomTranslation, idiomId={}", request.getIdiomId());
        return success(insertWordInfoService.insertWordIdiomTranslation(request));
    }

    @PostMapping("/sounds")
    public ApiResponse<String> insertWordSound(@Valid @RequestBody InsertWordSoundRequest request) {
        log.info("Request received: action=insertWordSound, wordId={}", request.getWordId());
        return success(insertWordInfoService.insertWordSound(request));
    }

    private ApiResponse<String> success(String result) {
        return ApiResponse.<String>builder()
                .message("Inserted successfully")
                .traceId(MDC.get("traceId"))
                .result(result)
                .build();
    }
}
