package com.bachdauduc.vocab_app.controller;

import com.bachdauduc.vocab_app.dto.request.exercise.UserLessonRequest;
import com.bachdauduc.vocab_app.dto.response.ApiResponse;
import com.bachdauduc.vocab_app.dto.response.exercise.ListenAndTypeLessonResponse;
import com.bachdauduc.vocab_app.dto.response.exercise.ListenExerciseSummaryResponse;
import com.bachdauduc.vocab_app.dto.response.exercise.ListeningCategoryResponse;
import com.bachdauduc.vocab_app.dto.response.exercise.UserLessonProgressResponse;
import com.bachdauduc.vocab_app.dto.response.exercise.UserLessonResponse;
import com.bachdauduc.vocab_app.dto.response.exercise.VocabReviewQuizResponse;
import com.bachdauduc.vocab_app.service.ExerciseService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/exercises")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ExerciseController {
    ExerciseService exerciseService;

    @PostMapping("/user-lessons")
    public ApiResponse<UserLessonResponse> addUserLesson(@Valid @RequestBody UserLessonRequest request) {
        log.info("Request received: action=addUserLesson, userId={}, lessonId={}, lessonType={}",
                request.getUserId(), request.getLessonId(), request.getLessonType());
        return success("User lesson added", exerciseService.addUserLesson(request));
    }

    @GetMapping("/user-lessons/progress")
    public ApiResponse<UserLessonProgressResponse> getUserLessonProgress(
            @RequestParam String userId,
            @RequestParam String lessonId,
            @RequestParam String lessonType
    ) {
        log.info("Request received: action=getUserLessonProgress, userId={}, lessonId={}, lessonType={}",
                userId, lessonId, lessonType);
        return success("Get user lesson progress successfully",
                exerciseService.getUserLessonProgress(userId, lessonId, lessonType));
    }

    @GetMapping("/listen-and-type/lesson")
    public ApiResponse<ListenAndTypeLessonResponse> getListenAndTypeLesson(
            @RequestParam String userId,
            @RequestParam String lessonId
    ) {
        log.info("Request received: action=getListenAndTypeLesson, userId={}, lessonId={}", userId, lessonId);
        return success("Get listen-and-type lesson successfully",
                exerciseService.getListenAndTypeLesson(userId, lessonId));
    }

    @GetMapping("/listen-and-type/categories")
    public ApiResponse<List<ListeningCategoryResponse>> getListenAndTypeCategories() {
        log.info("Request received: action=getListenAndTypeCategories");
        return success("Get listen-and-type categories successfully",
                exerciseService.getListenAndTypeCategories());
    }

    @GetMapping("/listen-and-type/sub-categories")
    public ApiResponse<List<String>> getListenAndTypeSubCategoryNames(
            @RequestParam String categoryId
    ) {
        log.info("Request received: action=getListenAndTypeSubCategoryNames, categoryId={}", categoryId);
        return success("Get listen-and-type sub categories successfully",
                exerciseService.getListenAndTypeSubCategoryNames(categoryId));
    }

    @GetMapping("/listen-and-type/lessons")
    public ApiResponse<List<ListenExerciseSummaryResponse>> getListenAndTypeLessonsBySubCategory(
            @RequestParam String subCategoryName,
            @RequestParam String userId
    ) {
        log.info("Request received: action=getListenAndTypeLessonsBySubCategory, subCategoryName={}, userId={}",
                subCategoryName, userId);
        return success("Get listen-and-type lessons successfully",
                exerciseService.getListenAndTypeLessonsBySubCategory(subCategoryName, userId));
    }

    @GetMapping("/vocab-review")
    public ApiResponse<List<VocabReviewQuizResponse>> getReviewVocabs(
            @RequestParam String userId,
            @RequestParam int totalReviewVocab,
            @RequestParam(defaultValue = "vi") String langCode
    ) {
        log.info("Request received: action=getReviewVocabs, userId={}, totalReviewVocab={}, langCode={}",
                userId, totalReviewVocab, langCode);
        return success("Get vocab review exercises successfully",
                exerciseService.getReviewVocabs(userId, totalReviewVocab, langCode));
    }

    @GetMapping("/vocab-review/word")
    public ApiResponse<List<VocabReviewQuizResponse>> getReviewVocab(
            @RequestParam String userId,
            @RequestParam String userVocabId,
            @RequestParam(defaultValue = "vi") String langCode
    ) {
        log.info("Request received: action=getReviewVocab, userId={}, userVocabId={}, langCode={}",
                userId, userVocabId, langCode);
        return success("Get vocab review exercise successfully",
                exerciseService.getReviewVocab(userId, userVocabId, langCode));
    }

    private <T> ApiResponse<T> success(String message, T result) {
        return ApiResponse.<T>builder()
                .message(message)
                .traceId(MDC.get("traceId"))
                .result(result)
                .build();
    }
}






