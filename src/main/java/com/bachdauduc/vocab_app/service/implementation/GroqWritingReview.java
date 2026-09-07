package com.bachdauduc.vocab_app.service.implementation;

import com.bachdauduc.vocab_app.entity.IeltsWritingExercise;
import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import com.bachdauduc.vocab_app.repository.IeltsWritingExerciseRepository;
import com.bachdauduc.vocab_app.service.abstraction.WritingReview;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GroqWritingReview implements WritingReview {
    static final String GROQ_CHAT_COMPLETIONS_URL = "https://api.groq.com/openai/v1/chat/completions";
    static final String MODEL = "openai/gpt-oss-120b";
    static final int MAX_ESSAY_CHARACTERS = 20_000;
    static final int MAX_STORED_TEXT_BYTES = 60_000;
    static final int MAX_COMPLETION_TOKENS = 5_000;
    static final long MAX_RATE_LIMIT_WAIT_MILLIS = 30_000;
    static final Pattern WORD_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+(?:['\u2019\\-][\\p{L}\\p{N}]+)*");
    static final List<String> CRITERIA = List.of(
            "task", "coherenceCohesion", "lexicalResource", "grammaticalRangeAccuracy");

    IeltsWritingExerciseRepository ieltsWritingExerciseRepository;
    ObjectMapper objectMapper;
    HttpClient httpClient;
    // Protects this application's instance only; no requests are queued here.
    Semaphore reviewPermit = new Semaphore(1);

    @NonFinal
    @Value("${groq.api.key:${grok.api.key:}}")
    String apiKey;

    @NonFinal String task1PromptTemplate;
    @NonFinal String task2PromptTemplate;
    @NonFinal JsonNode task1ResponseSchema;
    @NonFinal JsonNode task2ResponseSchema;

    @PostConstruct
    void loadReviewResources() {
        try {
            task1PromptTemplate = readClasspathText("prompts/ielts-writing-task-1.txt");
            task2PromptTemplate = readClasspathText("prompts/ielts-writing-task-2.txt");
            task1ResponseSchema = objectMapper.readTree(readClasspathText("schemas/ielts-writing-task-1-review.schema.json"));
            task2ResponseSchema = objectMapper.readTree(readClasspathText("schemas/ielts-writing-task-2-review.schema.json"));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load IELTS Writing review resources", exception);
        }
    }

    @Override
    public String generateReview(String exerciseId, String userId, String userAnswer) {
        if (!StringUtils.hasText(exerciseId) || !StringUtils.hasText(userId)
                || !StringUtils.hasText(userAnswer) || userAnswer.length() > MAX_ESSAY_CHARACTERS
                || userAnswer.getBytes(StandardCharsets.UTF_8).length > MAX_STORED_TEXT_BYTES) {
            throw new AppException(ErrorCode.INVALID_WRITING_REVIEW_REQUEST);
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new AppException(ErrorCode.GROQ_API_KEY_NOT_CONFIGURED);
        }
        IeltsWritingExercise exercise = ieltsWritingExerciseRepository.findById(exerciseId)
                .orElseThrow(() -> new AppException(ErrorCode.IELTS_WRITING_EXERCISE_NOT_FOUND));
        Integer taskType = exercise.getTaskType();
        if (taskType == null || (taskType != 1 && taskType != 2)) {
            throw new AppException(ErrorCode.WRITING_REVIEW_FAILED);
        }

        int wordCount = (int) WORD_PATTERN.matcher(userAnswer).results().count();
        JsonNode schema = taskType == 1 ? task1ResponseSchema : task2ResponseSchema;
        String systemPrompt = getSystemPrompt(exercise, taskType)
                + "\n\nFill the compact JSON template below and return the complete JSON object. "
                + "Replace every placeholder with review content. Arrays contain one example item only to show "
                + "their shape; return [] when there is no supported item. Preserve every key and add no keys.\n"
                + buildOutputTemplate(schema);
        ObjectNode learnerInput = objectMapper.createObjectNode()
                .put("taskStatement", exercise.getProblem().trim());
        if (taskType == 1) {
            learnerInput.put("imageDescription", exercise.getImageDescription().trim());
        }
        String learnerMessage = learnerInput.put("wordCount", wordCount)
                .put("learnerEssay", userAnswer).toString();

        if (!reviewPermit.tryAcquire()) {
            throw new AppException(ErrorCode.WRITING_REVIEW_RATE_LIMITED);
        }
        try {
            log.info("Generating IELTS Writing review: exerciseId={}, userId={}, taskType={}, wordCount={}, format=json_object",
                    exerciseId, userId, taskType, wordCount);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GROQ_CHAT_COMPLETIONS_URL))
                    .timeout(Duration.ofSeconds(120))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            buildRequestBody(systemPrompt, learnerMessage), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("Groq writing review response: exerciseId={}, attempt=1, status={}, {}",
                    exerciseId, response.statusCode(), summarizeMetrics(response));
            if (isRetryable(response)) {
                if (response.statusCode() == 429) {
                    long delayMillis = retryDelayMillis(response);
                    if (delayMillis > MAX_RATE_LIMIT_WAIT_MILLIS) {
                        log.warn("Groq retry delay exceeds writing review wait budget: exerciseId={}, delayMs={}",
                                exerciseId, delayMillis);
                        throw new AppException(ErrorCode.WRITING_REVIEW_RATE_LIMITED);
                    }
                    log.warn("Retrying rate-limited Groq writing review: exerciseId={}, delayMs={}",
                            exerciseId, delayMillis);
                    Thread.sleep(delayMillis);
                } else {
                    log.warn("Retrying Groq writing review after JSON validation failure: exerciseId={}",
                            exerciseId);
                }
                response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                log.info("Groq writing review response: exerciseId={}, attempt=2, status={}, {}",
                        exerciseId, response.statusCode(), summarizeMetrics(response));
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Groq writing review failed: status={}, exerciseId={}, providerError={}",
                        response.statusCode(), exerciseId, summarizeProviderError(response.body()));
                throw new AppException(response.statusCode() == 429
                        ? ErrorCode.WRITING_REVIEW_RATE_LIMITED : ErrorCode.WRITING_REVIEW_FAILED);
            }

            JsonNode choice = objectMapper.readTree(response.body()).path("choices").path(0);
            if (!"stop".equals(choice.path("finish_reason").asText())) {
                log.warn("Incomplete Groq writing review: exerciseId={}, finishReason={}",
                        exerciseId, safeErrorIdentifier(choice.path("finish_reason")));
                throw new AppException(ErrorCode.WRITING_REVIEW_FAILED);
            }
            String content = choice.path("message").path("content").asText(null);
            if (!StringUtils.hasText(content)) {
                log.warn("Empty Groq writing review: exerciseId={}", exerciseId);
                throw new AppException(ErrorCode.WRITING_REVIEW_FAILED);
            }
            // Validate at the application boundary as well, before saving.
            JsonNode parsed = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(content);
            validateReview(parsed, schema, "$");
            ObjectNode review = (ObjectNode) parsed;
            filterUnsupportedEvidence(review, "grammarErrors", "original", userAnswer);
            filterUnsupportedEvidence(review, "lexicalIssues", "original", userAnswer);
            filterUnsupportedEvidence(review, "successfulGrammar", "excerpt", userAnswer);
            enrichScores(review, taskType, wordCount);
            String result = objectMapper.writeValueAsString(review);
            if (result.getBytes(StandardCharsets.UTF_8).length > MAX_STORED_TEXT_BYTES) {
                throw new AppException(ErrorCode.WRITING_REVIEW_FAILED);
            }
            log.info("IELTS Writing review completed: exerciseId={}, taskType={}, overallBand={}",
                    exerciseId, taskType, review.path("overallBand").asDouble());
            return result;
        } catch (AppException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.WRITING_REVIEW_FAILED);
        } catch (Exception exception) {
            // Jackson exceptions and provider bodies may contain private learner content.
            log.error("Groq writing review failed: exerciseId={}, exceptionType={}",
                    exerciseId, exception.getClass().getSimpleName());
            throw new AppException(ErrorCode.WRITING_REVIEW_FAILED);
        } finally {
            reviewPermit.release();
        }
    }

    private String getSystemPrompt(IeltsWritingExercise exercise, int taskType) {
        if (!StringUtils.hasText(exercise.getProblem())
                || (taskType == 1 && !StringUtils.hasText(exercise.getImageDescription()))) {
            throw new AppException(ErrorCode.WRITING_REVIEW_FAILED);
        }
        return taskType == 1 ? task1PromptTemplate : task2PromptTemplate;
    }

    private String buildRequestBody(String systemPrompt, String learnerMessage) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", MODEL);
        body.put("temperature", 0.2);
        body.put("reasoning_effort", "low");
        body.put("max_completion_tokens", MAX_COMPLETION_TOKENS);
        body.putObject("response_format").put("type", "json_object");
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", learnerMessage);
        return objectMapper.writeValueAsString(body);
    }

    private JsonNode buildOutputTemplate(JsonNode schema) {
        return switch (schema.path("type").asText()) {
            case "object" -> {
                ObjectNode result = objectMapper.createObjectNode();
                schema.path("properties").fields().forEachRemaining(
                        field -> result.set(field.getKey(), buildOutputTemplate(field.getValue())));
                yield result;
            }
            case "array" -> objectMapper.createArrayNode().add(buildOutputTemplate(schema.path("items")));
            case "integer", "number" -> objectMapper.getNodeFactory().numberNode(0);
            case "boolean" -> objectMapper.getNodeFactory().booleanNode(false);
            case "string" -> objectMapper.getNodeFactory().textNode("");
            default -> throw new IllegalStateException("Unsupported writing review schema type");
        };
    }

    // Validates the types, required fields and object keys used by the two local schemas.
    private void validateReview(JsonNode value, JsonNode schema, String path) {
        boolean valid = value != null && switch (schema.path("type").asText()) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            default -> false;
        };
        if (!valid) {
            log.warn("Invalid writing review field: path={}", path);
            throw new AppException(ErrorCode.WRITING_REVIEW_FAILED);
        }
        if (value.isObject()) {
            JsonNode properties = schema.path("properties");
            for (JsonNode required : schema.path("required")) {
                String name = required.asText();
                validateReview(value.get(name), properties.path(name), path + "." + name);
            }
            if (schema.path("additionalProperties").isBoolean()
                    && !schema.path("additionalProperties").asBoolean()) {
                var fields = value.fieldNames();
                while (fields.hasNext()) {
                    if (!properties.has(fields.next())) {
                        throw new AppException(ErrorCode.WRITING_REVIEW_FAILED);
                    }
                }
            }
        } else if (value.isArray()) {
            for (JsonNode item : value) {
                validateReview(item, schema.path("items"), path + "[]");
            }
        }
    }

    private void enrichScores(ObjectNode review, int taskType, int wordCount) {
        ObjectNode scores = objectMapper.createObjectNode();
        double sum = 0;
        for (String criterion : CRITERIA) {
            JsonNode band = review.path("criteria").path(criterion).path("band");
            if (!band.isIntegralNumber() || !band.canConvertToInt() || band.asInt() < 0 || band.asInt() > 9) {
                throw new AppException(ErrorCode.WRITING_REVIEW_FAILED);
            }
            String name = "task".equals(criterion)
                    ? (taskType == 1 ? "taskAchievement" : "taskResponse") : criterion;
            scores.put(name, band.asInt());
            sum += band.asInt();
        }
        review.put("taskType", taskType);
        review.put("wordCount", wordCount);
        review.put("overallBand", Math.round(sum / 2.0) / 2.0);
        review.set("scores", scores);
    }

    private void filterUnsupportedEvidence(ObjectNode review, String field, String quoteField, String essay) {
        ArrayNode supported = objectMapper.createArrayNode();
        String normalizedEssay = essay.replaceAll("\\s+", " ").trim();
        for (JsonNode item : review.path(field)) {
            String quote = item.path(quoteField).asText("").replaceAll("\\s+", " ").trim();
            if (!quote.isEmpty() && normalizedEssay.contains(quote)) {
                supported.add(item);
            }
        }
        review.set(field, supported);
    }

    private boolean isJsonValidationFailure(HttpResponse<String> response) {
        if (response.statusCode() != 400) {
            return false;
        }
        try {
            return "json_validate_failed".equals(
                    objectMapper.readTree(response.body()).path("error").path("code").asText());
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isRetryable(HttpResponse<String> response) {
        return response.statusCode() == 429 || isJsonValidationFailure(response);
    }

    private long retryDelayMillis(HttpResponse<String> response) {
        if (response.headers() == null) {
            return 1_000;
        }
        String value = response.headers().firstValue("Retry-After").orElse("1");
        try {
            double seconds = Double.parseDouble(value);
            if (!Double.isFinite(seconds) || seconds < 0) {
                return 1_000;
            }
            return (long) Math.ceil(seconds * 1_000);
        } catch (NumberFormatException ignored) {
            return 1_000;
        }
    }

    private String summarizeMetrics(HttpResponse<String> response) {
        JsonNode usage = objectMapper.createObjectNode();
        try {
            JsonNode root = objectMapper.readTree(response.body());
            if (root != null) {
                usage = root.path("usage");
            }
        } catch (Exception ignored) {
            // Metrics are optional and must never prevent review/error handling.
        }
        return "prompt=" + tokenCount(usage.path("prompt_tokens"))
                + ",completion=" + tokenCount(usage.path("completion_tokens"))
                + ",total=" + tokenCount(usage.path("total_tokens"))
                + ",reasoning=" + tokenCount(usage.at("/completion_tokens_details/reasoning_tokens"))
                + ",cached=" + tokenCount(usage.at("/prompt_tokens_details/cached_tokens"))
                + ",remaining=" + safeRateHeader(response, "x-ratelimit-remaining-tokens", "[0-9]{1,18}")
                + ",reset=" + safeRateHeader(response, "x-ratelimit-reset-tokens",
                        "(?:[0-9]{1,9}(?:\\.[0-9]{1,9})?(?:ms|s|m|h))+");
    }

    private long tokenCount(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0
                ? value.longValue() : -1;
    }

    private String safeRateHeader(HttpResponse<String> response, String header, String pattern) {
        String value = response.headers() == null ? ""
                : response.headers().firstValue(header).orElse("");
        return value.length() <= 64 && value.matches(pattern) ? value : "unknown";
    }

    private String summarizeProviderError(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.path("error").isObject()) {
                return "unrecognized_error_body";
            }
            JsonNode error = root.path("error");
            return "code=" + safeErrorIdentifier(error.path("code"))
                    + ",type=" + safeErrorIdentifier(error.path("type"))
                    + ",param=" + safeErrorIdentifier(error.path("param"))
                    + ",generationChars=" + (error.path("failed_generation").isTextual()
                            ? error.path("failed_generation").textValue().length() : -1)
                    + ",generationKind=" + classifyFailedGeneration(error.path("failed_generation"));
        } catch (Exception ignored) {
            return "unparseable_error_body";
        }
    }

    private String classifyFailedGeneration(JsonNode generation) {
        if (!generation.isTextual()) {
            return "unavailable";
        }
        String text = generation.textValue();
        if (text.isBlank()) {
            return "empty";
        }
        try {
            JsonNode parsed = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text);
            return parsed != null && parsed.isObject() ? "json_object" : "other_json";
        } catch (com.fasterxml.jackson.core.io.JsonEOFException ignored) {
            return "incomplete_json";
        } catch (Exception ignored) {
            return "non_json";
        }
    }

    private String safeErrorIdentifier(JsonNode value) {
        if (!value.isTextual()) {
            return "unknown";
        }
        String identifier = value.textValue();
        return identifier.matches("[a-z0-9_.\\[\\]-]{1,100}") ? identifier : "redacted";
    }

    private String readClasspathText(String path) throws Exception {
        try (InputStream stream = new ClassPathResource(path).getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
