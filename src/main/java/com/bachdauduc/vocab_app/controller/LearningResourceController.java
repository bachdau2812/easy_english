package com.bachdauduc.vocab_app.controller;

import com.bachdauduc.vocab_app.dto.request.learningresource.InsertIeltsReadingSourceRequest;
import com.bachdauduc.vocab_app.dto.request.learningresource.IeltsWritingReviewRequest;
import com.bachdauduc.vocab_app.dto.response.ApiResponse;
import com.bachdauduc.vocab_app.dto.response.learningresource.IeltsReadingQuizResponse;
import com.bachdauduc.vocab_app.dto.response.learningresource.IeltsReadingSourceResponse;
import com.bachdauduc.vocab_app.dto.response.learningresource.IeltsWritingProblemSummaryResponse;
import com.bachdauduc.vocab_app.entity.IeltsWritingExercise;
import com.bachdauduc.vocab_app.entity.IeltsWritingReference;
import com.bachdauduc.vocab_app.entity.UserVocabAttempt;
import com.bachdauduc.vocab_app.service.LearningResourceService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/learning-resources")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LearningResourceController {
    LearningResourceService learningResourceService;

    @GetMapping("/ielts-reading-sources")
    public ApiResponse<Page<IeltsReadingSourceResponse>> getIeltsReadingSources(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        log.info("Request received: action=getIeltsReadingSources, page={}, limit={}", page, limit);
        return success("Get IELTS reading sources successfully",
                learningResourceService.getIeltsReadingSources(page, limit));
    }

    @GetMapping("/ielts-reading-sources/categories")
    public ApiResponse<List<String>> getIeltsReadingCategories() {
        log.info("Request received: action=getIeltsReadingCategories");
        return success("Get IELTS reading categories successfully",
                learningResourceService.getIeltsReadingCategories());
    }

    @GetMapping("/ielts-reading-sources/by-category")
    public ApiResponse<Page<IeltsReadingSourceResponse>> getIeltsReadingSourcesByCategory(
            @RequestParam String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        log.info("Request received: action=getIeltsReadingSourcesByCategory, name={}, page={}, limit={}",
                name, page, limit);
        return success("Get IELTS reading sources by category successfully",
                learningResourceService.getIeltsReadingSourcesByCategory(name, page, limit));
    }

    @GetMapping("/ielts-reading-sources/{readingId}/quiz")
    public ApiResponse<IeltsReadingQuizResponse> getIeltsReadingQuiz(
            @PathVariable String readingId,
            @RequestParam String userId
    ) {
        log.info("Request received: action=getIeltsReadingQuiz, readingId={}, userId={}", readingId, userId);
        return success("Get IELTS reading quiz successfully",
                learningResourceService.getIeltsReadingQuiz(readingId, userId));
    }

    @PostMapping("/ielts-reading-sources")
    public ApiResponse<IeltsReadingSourceResponse> insertIeltsReadingSource(
            @Valid @RequestBody InsertIeltsReadingSourceRequest request
    ) {
        log.info("Request received: action=insertIeltsReadingSource, categorySlug={}, title={}",
                request.getCategorySlug(), request.getTitle());
        return success("IELTS reading source inserted",
                learningResourceService.insertIeltsReadingSource(request));
    }

    @GetMapping("/ielts-writing/topics")
    public ApiResponse<List<String>> getIeltsWritingTopics(@RequestParam Integer taskType) {
        log.info("Request received: action=getIeltsWritingTopics, taskType={}", taskType);
        return success("Get IELTS writing topics successfully",
                learningResourceService.getIeltsWritingTopics(taskType));
    }

    @GetMapping("/ielts-writing/problems")
    public ApiResponse<List<IeltsWritingProblemSummaryResponse>> getIeltsWritingProblemsByTopic(
            @RequestParam(name = "topic_name", required = false) String topicName,
            @RequestParam(name = "topicName", required = false) String topicNameAlias,
            @RequestParam String userId
    ) {
        String resolvedTopicName = topicName != null ? topicName : topicNameAlias;
        log.info("Request received: action=getIeltsWritingProblemsByTopic, topicName={}, userId={}", resolvedTopicName, userId);
        return success("Get IELTS writing problems successfully",
                learningResourceService.getIeltsWritingProblemsByTopic(resolvedTopicName, userId));
    }

    @GetMapping("/ielts-writing/problems/{problemId}")
    public ApiResponse<IeltsWritingExercise> getIeltsWritingProblem(@PathVariable String problemId) {
        log.info("Request received: action=getIeltsWritingProblem, problemId={}", problemId);
        return success("Get IELTS writing problem successfully",
                learningResourceService.getIeltsWritingProblem(problemId));
    }

    @GetMapping("/ielts-writing/problems/{problemId}/bands")
    public ApiResponse<List<String>> getIeltsWritingReferenceBands(@PathVariable String problemId) {
        log.info("Request received: action=getIeltsWritingReferenceBands, problemId={}", problemId);
        return success("Get IELTS writing reference bands successfully",
                learningResourceService.getIeltsWritingReferenceBands(problemId));
    }

    @GetMapping("/ielts-writing/problems/{problemId}/references")
    public ApiResponse<List<IeltsWritingReference>> getIeltsWritingReferences(
            @PathVariable String problemId,
            @RequestParam String band
    ) {
        log.info("Request received: action=getIeltsWritingReferences, problemId={}, band={}", problemId, band);
        return success("Get IELTS writing references successfully",
                learningResourceService.getIeltsWritingReferences(problemId, band));
    }


    @GetMapping("/ielts-writing/attempt-history")
    public ApiResponse<List<UserVocabAttempt>> getIeltsWritingAttemptHistory(
            @RequestParam String userId,
            @RequestParam String exerciseId
    ) {
        log.info("Request received: action=getIeltsWritingAttemptHistory, userId={}, exerciseId={}", userId, exerciseId);
        return success("Get IELTS writing attempt history successfully",
                learningResourceService.getIeltsWritingAttemptHistory(userId, exerciseId));
    }
    @PostMapping("/ielts-writing/reviews")
    public ApiResponse<String> reviewIeltsWriting(@Valid @RequestBody IeltsWritingReviewRequest request) {
        log.info("Request received: action=reviewIeltsWriting, exerciseId={}, userId={}",
                request.getExerciseId(), request.getUserId());
        return success("Review IELTS writing successfully",
                learningResourceService.reviewIeltsWriting(request.getExerciseId(), request.getUserId(), request.getUserAnswer()));
    }
    @PostMapping("/listen-exercises")
    public ApiResponse<String> insertListenExercisePlaceholder() {
        return success("Placeholder", learningResourceService.insertListenExercisePlaceholder());
    }

    @PostMapping("/quizzes/listen-and-type")
    public ApiResponse<String> insertListenAndTypeQuizPlaceholder() {
        return success("Placeholder", learningResourceService.insertListenAndTypeQuizPlaceholder());
    }

    @PostMapping("/quizzes/listen-and-answer")
    public ApiResponse<String> insertListenAndAnswerQuizPlaceholder() {
        return success("Placeholder", learningResourceService.insertListenAndAnswerQuizPlaceholder());
    }

    @PostMapping("/quizzes/reading")
    public ApiResponse<String> insertReadingQuizPlaceholder() {
        return success("Placeholder", learningResourceService.insertReadingQuizPlaceholder());
    }

    @PostMapping("/quizzes/generate-reading-listening")
    public ApiResponse<String> generateReadingAndListeningQuizPlaceholder() {
        return success("Placeholder", learningResourceService.generateReadingAndListeningQuizPlaceholder());
    }

    private <T> ApiResponse<T> success(String message, T result) {
        return ApiResponse.<T>builder()
                .message(message)
                .traceId(MDC.get("traceId"))
                .result(result)
                .build();
    }
}