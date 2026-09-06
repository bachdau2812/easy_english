package com.bachdauduc.vocab_app.service.implementation;

import com.bachdauduc.vocab_app.dto.request.AzureTranslatorRequest;
import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import com.bachdauduc.vocab_app.service.abstraction.GetTranslation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AzureTranslator implements GetTranslation {
    ObjectMapper objectMapper;

    @NonFinal
    @Value("${azure_api_key}")
    String apiKey;

    @NonFinal
    @Value("${azure_endpoint}")
    String endpoint;

    @NonFinal
    @Value("${azure_region}")
    String region;

    @Override
    public String translateHtml(String html, String transLangCode) {
        return translate(List.of(html), transLangCode, "html").get(html);
    }

    @Override
    public Map<String, String> translate(List<String> texts, String transLangCode) {
        return translate(texts, transLangCode, "plain");
    }

    private Map<String, String> translate(List<String> texts, String transLangCode, String textType) {
        if (CollectionUtils.isEmpty(texts)) {
            return Map.of();
        }
        if (!StringUtils.hasText(transLangCode)) {
            throw new AppException(ErrorCode.TRANSLATION_FAILED);
        }

        try {
            List<AzureTranslatorRequest> body = texts.stream()
                    .map(text -> AzureTranslatorRequest.builder().text(text).build())
                    .toList();

            HttpRequest request = HttpRequest.newBuilder()
                    .timeout(Duration.ofSeconds(20))
                    .uri(URI.create(buildTranslateUri(transLangCode, textType)))
                    .header("Ocp-Apim-Subscription-Key", apiKey)
                    .header("Ocp-Apim-Subscription-Region", region)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response;
            try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
                response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Azure translation failed: status={}", response.statusCode());
                throw new AppException(ErrorCode.TRANSLATION_FAILED);
            }

            JsonNode responseBody = objectMapper.readTree(response.body());
            Map<String, String> translatedByOriginalText = new LinkedHashMap<>();
            for (int i = 0; i < texts.size(); i++) {
                JsonNode translation = responseBody.path(i).path("translations").path(0);
                translatedByOriginalText.put(texts.get(i), translation.path("text").asText(null));
            }
            return translatedByOriginalText;
        } catch (AppException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.TRANSLATION_FAILED);
        } catch (Exception exception) {
            log.error("Azure translation failed", exception);
            throw new AppException(ErrorCode.TRANSLATION_FAILED);
        }
    }

    private String buildTranslateUri(String transLangCode, String textType) {
        String baseEndpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return baseEndpoint + "/translate?api-version=3.0&from=en&to="
                + URLEncoder.encode(transLangCode, StandardCharsets.UTF_8) + "&textType=" + textType;
    }
}
