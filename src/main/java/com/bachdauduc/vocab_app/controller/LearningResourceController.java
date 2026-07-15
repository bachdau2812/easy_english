package com.bachdauduc.vocab_app.controller;

import com.bachdauduc.vocab_app.dto.request.learningresource.InsertIeltsReadingSourceRequest;
import com.bachdauduc.vocab_app.dto.response.ApiResponse;
import com.bachdauduc.vocab_app.dto.response.learningresource.IeltsReadingSourceResponse;
import com.bachdauduc.vocab_app.service.LearningResourceService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/ielts-reading-sources")
    public ApiResponse<IeltsReadingSourceResponse> insertIeltsReadingSource(
            @Valid @RequestBody InsertIeltsReadingSourceRequest request
    ) {
        log.info("Request received: action=insertIeltsReadingSource, categorySlug={}, title={}",
                request.getCategorySlug(), request.getTitle());
        return success("IELTS reading source inserted",
                learningResourceService.insertIeltsReadingSource(request));
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
