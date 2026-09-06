package com.bachdauduc.vocab_app.service.implementation;

import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AzureTranslatorTest {
    private HttpServer server;
    private AzureTranslator translator;
    private final AtomicReference<String> query = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String response = "[{\"translations\":[{\"text\":\"Xin chào.\",\"to\":\"vi\"}]}]";

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/translate", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        translator = new AzureTranslator(new ObjectMapper());
        ReflectionTestUtils.setField(translator, "endpoint", "http://127.0.0.1:" + server.getAddress().getPort() + "/");
        ReflectionTestUtils.setField(translator, "apiKey", "test-key");
        ReflectionTestUtils.setField(translator, "region", "test-region");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void htmlTranslationSendsOneHtmlTextWithProtectedMarker() throws Exception {
        String html = "Hello. <span translate=\"no\">[[SEG_1]]</span>";
        response = new ObjectMapper().writeValueAsString(List.of(
                java.util.Map.of("translations", List.of(java.util.Map.of(
                        "text", "Xin chào. <span translate=\"no\">[[SEG_1]]</span>", "to", "vi")))));

        assertThat(translator.translateHtml(html, "vi"))
                .isEqualTo("Xin chào. <span translate=\"no\">[[SEG_1]]</span>");
        assertThat(query.get()).contains("textType=html", "from=en", "to=vi", "api-version=3.0");
        var request = new ObjectMapper().readTree(body.get());
        assertThat(request.size()).isEqualTo(1);
        assertThat(request.get(0).path("Text").asText()).isEqualTo(html);
    }

    @Test
    void dictionaryTranslationKeepsPlainMode() {
        assertThat(translator.translate(List.of("Hello."), "vi")).containsEntry("Hello.", "Xin chào.");
        assertThat(query.get()).doesNotContain("textType=html");
    }

    @Test
    void htmlProviderFailureUsesExistingTranslationError() {
        status = 503;
        assertThatThrownBy(() -> translator.translateHtml("Hello.", "vi"))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.TRANSLATION_FAILED));
    }
}