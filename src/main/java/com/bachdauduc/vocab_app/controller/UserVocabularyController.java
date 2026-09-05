package com.bachdauduc.vocab_app.controller;

import com.bachdauduc.vocab_app.dto.request.uservocabulary.SubmitReviewAttemptRequest;
import com.bachdauduc.vocab_app.dto.request.uservocabulary.UserSearchHistoryRequest;
import com.bachdauduc.vocab_app.dto.request.uservocabulary.UserVocabularyRequest;
import com.bachdauduc.vocab_app.dto.response.ApiResponse;
import com.bachdauduc.vocab_app.dto.response.uservocabulary.UserSearchHistoryResponse;
import com.bachdauduc.vocab_app.dto.response.uservocabulary.UserVocabAttemptResponse;
import com.bachdauduc.vocab_app.dto.response.uservocabulary.UserVocabularyInfoResponse;
import com.bachdauduc.vocab_app.dto.response.uservocabulary.UserVocabularyResponse;
import com.bachdauduc.vocab_app.dto.response.uservocabulary.UserVocabularyStatisticResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordResponse;
import com.bachdauduc.vocab_app.service.UserVocabularyService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/user-vocabularies")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserVocabularyController {
    UserVocabularyService userVocabularyService;

    @PostMapping
    public ApiResponse<UserVocabularyResponse> addUserVocab(@Valid @RequestBody UserVocabularyRequest request) {
        log.info("Request received: action=addUserVocab, userId={}, wordId={}",
                request.getUserId(), request.getWordId());
        return success("User vocabulary added", userVocabularyService.addUserVocab(request));
    }

    @PostMapping("/review-attempts")
    public ApiResponse<UserVocabAttemptResponse> submitReviewAttempt(
            @Valid @RequestBody SubmitReviewAttemptRequest request
    ) {
        log.info("Request received: action=submitReviewAttempt, userId={}, exerciseType={}",
                request.getUserId(), request.getExerciseType());
        return success("User vocab attempt saved", userVocabularyService.submitReviewAttempt(request));
    }

    @PostMapping("/search-history")
    public ApiResponse<UserSearchHistoryResponse> insertUserHistory(
            @Valid @RequestBody UserSearchHistoryRequest request
    ) {
        log.info("Request received: action=insertUserHistory, userId={}, wordId={}",
                request.getUserId(), request.getWordId());
        return success("User search history saved", userVocabularyService.insertUserHistory(request));
    }

    @GetMapping("/search")
    public ApiResponse<? extends Page<?>> searchUserVocabulary(
            Authentication authentication,
            @RequestParam String text,
            @RequestParam(defaultValue = "false") boolean isAutocomplete,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        String userId = authentication.getName();
        log.info(
                "Request received: action=searchUserVocabulary, userId={}, isAutocomplete={}, page={}, limit={}",
                userId,
                isAutocomplete,
                page,
                limit
        );
        if (isAutocomplete) {
            return success("Search user vocabularies successfully",
                    userVocabularyService.autocompleteUserVocabulary(userId, text, page, limit));
        }
        return success("Search user vocabularies successfully",
                userVocabularyService.searchUserVocabulary(userId, text, page, limit));
    }

    @GetMapping("/search-history")
    public ApiResponse<Page<UserSearchHistoryResponse>> getUserHistory(
            @RequestParam String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        log.info("Request received: action=getUserHistory, userId={}", userId);
        return success("Get user search history successfully",
                userVocabularyService.getUserHistory(userId, page, limit));
    }

    @GetMapping("/attempts")
    public ApiResponse<Page<UserVocabAttemptResponse>> getUserAttemptListByDay(
            @RequestParam String userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String type
    ) {
        log.info("Request received: action=getUserAttemptListByDay, userId={}, from={}, to={}, type={}",
                userId, from, to, type);
        return success("Get user attempts successfully",
                userVocabularyService.getUserAttemptListByDay(userId, from, to, page, limit, type));
    }

    @GetMapping("/by-level")
    public ApiResponse<Page<UserVocabularyResponse>> getUserVocabByLevel(
            @RequestParam String userId,
            @RequestParam int level,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        log.info("Request received: action=getUserVocabByLevel, userId={}, level={}", userId, level);
        return success("Get user vocabularies successfully",
                userVocabularyService.getUserVocabByLevel(userId, level, page, limit));
    }

    @GetMapping("/info")
    public ApiResponse<UserVocabularyInfoResponse> getUserVocabularyInfo(
            @RequestParam String userId,
            @RequestParam String infoType
    ) {
        log.info("Request received: action=getUserVocabularyInfo, userId={}, infoType={}",
                userId, infoType);
        return success("Get user vocabulary info successfully",
                userVocabularyService.getUserVocabularyInfo(userId, infoType));
    }

    @GetMapping("/statistics/daily")
    public ApiResponse<UserVocabularyStatisticResponse> getUserDailyStatistic(@RequestParam String userId) {
        log.info("Request received: action=getUserDailyStatistic, userId={}", userId);
        return success("Get user daily statistic successfully", userVocabularyService.getUserDailyStatistic(userId));
    }

    @GetMapping("/statistics/overall")
    public ApiResponse<UserVocabularyStatisticResponse> getUserOverallStatistic(@RequestParam String userId) {
        log.info("Request received: action=getUserOverallStatistic, userId={}", userId);
        return success("Get user overall statistic successfully", userVocabularyService.getUserOverallStatistic(userId));
    }

    @GetMapping("/{userVocabId}/word")
    public ApiResponse<WordResponse> getUserVocabWord(@PathVariable String userVocabId) {
        log.info("Request received: action=getUserVocabWord, userVocabId={}", userVocabId);
        return success("Get user vocabulary word successfully", userVocabularyService.getUserVocabWord(userVocabId));
    }

    private <T> ApiResponse<T> success(String message, T result) {
        return ApiResponse.<T>builder()
                .message(message)
                .traceId(MDC.get("traceId"))
                .result(result)
                .build();
    }
}
