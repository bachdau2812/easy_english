package com.bachdauduc.vocab_app.service.implementation;

import com.bachdauduc.vocab_app.service.model.GeneratedWordExample;
import com.bachdauduc.vocab_app.service.model.WordExampleGenerationInput;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroqWordExampleGeneratorTest {
    @Mock HttpClient httpClient;
    @Mock HttpResponse<String> httpResponse;

    ObjectMapper objectMapper;
    GroqWordExampleGenerator generator;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        generator = new GroqWordExampleGenerator(httpClient, objectMapper, "test-api-key");
    }

    @Test
    void generateUsesStrictSchemaAndReturnsEveryValidExampleForTheSameSense() throws Exception {
        WordExampleGenerationInput input = new WordExampleGenerationInput(
                "request-1", "word-1", "sense-1", "bank", "noun", "B1",
                "the land alongside a river", 2);
        stubSuccessfulResponse(List.of(Map.of(
                "requestId", "request-1", "wordId", "word-1", "senseId", "sense-1",
                "examples", List.of(
                        example("We sat on the bank beside the river.", "Chung toi ngoi tren bo song."),
                        example("Flowers grew along the bank of the stream.", "Hoa moc doc bo suoi."))
        )));

        List<GeneratedWordExample> result = generator.generate(List.of(input));

        assertThat(result).hasSize(2).allSatisfy(item -> {
            assertThat(item.wordId()).isEqualTo("word-1");
            assertThat(item.senseId()).isEqualTo("sense-1");
        });
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any());
        JsonNode body = objectMapper.readTree(readBody(captor.getValue()));
        assertThat(body.at("/messages/0/role").asText()).isEqualTo("system");
        assertThat(body.at("/messages/1/role").asText()).isEqualTo("user");
        assertThat(body.at("/response_format/json_schema/strict").asBoolean()).isTrue();
        assertThat(objectMapper.readTree(body.at("/messages/1/content").asText())
                .at("/items/0/requiredExampleCount").asInt()).isEqualTo(2);
    }

    @Test
    void generateFiltersItemsThatCannotBeSafelyMappedOrUsedBySentenceQuiz() throws Exception {
        WordExampleGenerationInput input = new WordExampleGenerationInput(
                "request-1", "word-1", "sense-1", "bank", "noun", null,
                "the land alongside a river", 4);
        stubSuccessfulResponse(List.of(
                Map.of("requestId", "request-1", "wordId", "wrong", "senseId", "sense-1",
                        "examples", List.of(example("They sat on the bank.", "Ho ngoi tren bo."))),
                Map.of("requestId", "request-1", "wordId", "word-1", "senseId", "sense-1",
                        "examples", List.of(
                                example("Banking services close at five.", "Dich vu dong cua luc nam gio."),
                                example("They sat on the bank beside the river.", "Ho ngoi tren bo song."),
                                example("They sat on the bank beside the river.", "Ban dich trung."),
                                example(" ", "Ban dich khong hop le.")))
        ));

        List<GeneratedWordExample> result = generator.generate(List.of(input));

        assertThat(result).containsExactly(new GeneratedWordExample(
                "request-1", "word-1", "sense-1",
                "They sat on the bank beside the river.", "Ho ngoi tren bo song."));
    }

    private Map<String, String> example(String text, String translation) {
        return Map.of("example", text, "translatedExample", translation);
    }

    private void stubSuccessfulResponse(List<Map<String, Object>> items) throws Exception {
        String content = objectMapper.writeValueAsString(Map.of("items", items));
        String body = objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", content)))));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(body);
        when(httpClient.send(
                any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()
        )).thenReturn(httpResponse);
    }

    private String readBody(HttpRequest request) {
        CompletableFuture<String> result = new CompletableFuture<>();
        StringBuilder body = new StringBuilder();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer item) { body.append(StandardCharsets.UTF_8.decode(item)); }
            @Override public void onError(Throwable throwable) { result.completeExceptionally(throwable); }
            @Override public void onComplete() { result.complete(body.toString()); }
        });
        return result.join();
    }
}
