package com.bachdauduc.vocab_app.service.implementation;

import com.bachdauduc.vocab_app.entity.IeltsWritingExercise;
import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import com.bachdauduc.vocab_app.repository.IeltsWritingExerciseRepository;
import com.bachdauduc.vocab_app.service.abstraction.WritingReview;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GroqWritingReview implements WritingReview {
    static final String GROQ_CHAT_COMPLETIONS_URL = "https://api.groq.com/openai/v1/chat/completions";
    static final String MODEL = "openai/gpt-oss-120b";

    IeltsWritingExerciseRepository ieltsWritingExerciseRepository;
    ObjectMapper objectMapper;

    @NonFinal
    @Value("${grok.api.key}")
    String apiKey;

    @Override
    public String generateReview(String exerciseId, String userId, String userAnswer) {
        if (!StringUtils.hasText(exerciseId) || !StringUtils.hasText(userId) || !StringUtils.hasText(userAnswer)) {
            throw new AppException(ErrorCode.INVALID_WRITING_REVIEW_REQUEST);
        }

        if (!StringUtils.hasText(apiKey)) {
            log.error("Groq API key is not configured: property=grok.api.key, env=GROK_API_KEY");
            throw new AppException(ErrorCode.GROQ_API_KEY_NOT_CONFIGURED);
        }

        String normalizedApiKey = apiKey.trim();

        IeltsWritingExercise exercise = ieltsWritingExerciseRepository.findById(exerciseId)
                .orElseThrow(() -> new AppException(ErrorCode.IELTS_WRITING_EXERCISE_NOT_FOUND));

        String finalPrompt = buildFinalPrompt(exercise.getEvaluationPrompt(), userAnswer);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_CHAT_COMPLETIONS_URL))
                    .header("Authorization", "Bearer " + normalizedApiKey)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(finalPrompt), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Groq writing review failed: status={}, exerciseId={}, userId={}, responseBody={}",
                        response.statusCode(), exerciseId, userId, response.body());
                throw new AppException(ErrorCode.WRITING_REVIEW_FAILED);
            }

            JsonNode responseBody = objectMapper.readTree(response.body());
            String content = responseBody.path("choices").path(0).path("message").path("content").asText(null);
            if (!StringUtils.hasText(content)) {
                throw new AppException(ErrorCode.WRITING_REVIEW_FAILED);
            }
            return content;
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Groq writing review failed: exerciseId={}, userId={}", exerciseId, userId, exception);
            throw new AppException(ErrorCode.WRITING_REVIEW_FAILED);
        }
    }

    private String buildRequestBody(String finalPrompt) throws Exception {
        Map<String, Object> body = Map.of(
                "model", MODEL,
                "temperature", 0.1,
                "messages", List.of(
                        Map.of("role", "user", "content", finalPrompt)
                )
        );
        return objectMapper.writeValueAsString(body);
    }

    private String buildFinalPrompt(String evaluationPrompt, String userAnswer) {
        return strictSystemPrompt()
                + "\n\nBase examiner prompt and task context:\n"
                + evaluationPrompt
                + "\n\nLearner essay to grade:\n"
                + userAnswer;
    }

    private String strictSystemPrompt() {
        return """
            You are an experienced IELTS Writing examiner.

            Grade the learner's response fairly and consistently according to the official IELTS Writing band descriptors, using a balanced best-fit approach.

            Evaluate these criteria independently:
            - Task Response or Task Achievement
            - Coherence and Cohesion
            - Lexical Resource
            - Grammatical Range and Accuracy

            Scoring principles:
            - Assess the learner's overall demonstrated ability across the entire response.
            - Consider both strengths and weaknesses before assigning each band score.
            - Distinguish isolated mistakes from recurring errors that genuinely reduce clarity or control.
            - Do not lower a band substantially for a small number of minor errors when meaning remains clear.
            - Do not penalize the same issue under multiple criteria unless it independently affects each criterion.
            - Do not require perfect performance for a particular band.
            - When most features of a higher band are demonstrated and weaknesses do not seriously limit communication, prefer the higher supported band.
            - Avoid both systematic under-scoring and unjustified score inflation.
            - Use reference essays and calibration data only as guidance, not as a strict ceiling or required template.

            Feedback requirements:
            - Base every score and comment only on the supplied task, learner essay, assessment instructions, visual information, and reference calibration.
            - Do not invent errors, missing content, quotations, or unsupported claims.
            - Support important strengths and weaknesses with identifiable evidence from the essay.
            - Provide detailed, criterion-specific feedback and actionable improvement advice.
            - Grammar feedback must analyze both range and accuracy.
            - For important grammar errors, quote the original English excerpt, provide a corrected English version, identify the error type, explain it in Vietnamese, and state whether it is isolated or recurring.
            - Also identify successful grammatical structures instead of discussing only errors.
            - Prioritize representative and recurring problems rather than listing every minor mistake.

            Language requirements:
            - Write all explanations, strengths, weaknesses, and improvement advice in Vietnamese.
            - Keep quotations from the learner's essay and suggested corrections in English.

            Output requirements:
            - Follow exactly the JSON structure requested in the user prompt.
            - Preserve all required keys and do not add new top-level keys.
            - All band scores must be JSON numbers, not strings.
            - Use arrays even when they contain one item, and use empty arrays when no supported item exists.
            - Do not return null.
            - Return only one valid JSON object.
            - Do not use Markdown code fences or include any text outside the JSON object.
            """;
    }
}