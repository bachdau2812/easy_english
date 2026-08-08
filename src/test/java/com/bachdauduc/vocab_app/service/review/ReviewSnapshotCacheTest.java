package com.bachdauduc.vocab_app.service.review;

import com.bachdauduc.vocab_app.properties.RedisKeyProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewSnapshotCacheTest {
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock RedisKeyProperties redisKeyProperties;

    ObjectMapper objectMapper;
    ReviewSnapshotCache cache;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        cache = new ReviewSnapshotCache(
                redisTemplate, redisKeyProperties, objectMapper, new Random(1), true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisKeyProperties.reviewVocabSnapshotKey(
                "word-1", "sense:sense-1", "vi", 2L
        )).thenReturn("snapshot:word-1:sense:sense-1:vi:2");
    }

    @Test
    void returnsValidSnapshotFromRevisionedBatchLookup() throws Exception {
        ReviewSnapshotIdentity identity =
                new ReviewSnapshotIdentity("uv-1", "word-1", "sense:sense-1", "vi");
        ReviewVocabSnapshot snapshot = snapshot();
        when(redisKeyProperties.reviewVocabRevisionKey("word-1")).thenReturn("revision:word-1");
        when(valueOperations.multiGet(List.of("revision:word-1"))).thenReturn(List.of("2"));
        when(valueOperations.multiGet(List.of("snapshot:word-1:sense:sense-1:vi:2")))
                .thenReturn(List.of(objectMapper.writeValueAsString(snapshot)));

        ReviewSnapshotLookup lookup = cache.lookup(List.of(identity));

        assertThat(lookup.hits()).containsEntry("uv-1", snapshot);
        assertThat(lookup.wordRevisions()).containsEntry("word-1", 2L);
    }

    @Test
    void treatsCorruptSnapshotAsCacheMiss() {
        ReviewSnapshotIdentity identity =
                new ReviewSnapshotIdentity("uv-1", "word-1", "sense:sense-1", "vi");
        when(redisKeyProperties.reviewVocabRevisionKey("word-1")).thenReturn("revision:word-1");
        when(valueOperations.multiGet(List.of("revision:word-1"))).thenReturn(List.of("2"));
        when(valueOperations.multiGet(List.of("snapshot:word-1:sense:sense-1:vi:2")))
                .thenReturn(List.of("{broken"));

        ReviewSnapshotLookup lookup = cache.lookup(List.of(identity));

        assertThat(lookup.hits()).isEmpty();
        assertThat(lookup.wordRevisions()).containsEntry("word-1", 2L);
    }

    @Test
    void writesSnapshotWithSixHourTtlAndJitter() {
        ReviewVocabSnapshot snapshot = snapshot();
        ReviewSnapshotLookup lookup = new ReviewSnapshotLookup(Map.of(), Map.of("word-1", 2L));
        when(redisKeyProperties.reviewVocabSnapshotTtl()).thenReturn(Duration.ofHours(6));
        when(redisKeyProperties.reviewVocabSnapshotJitter()).thenReturn(Duration.ofMinutes(30));
        doAnswer(invocation -> {
            SessionCallback<?> callback = invocation.getArgument(0);
            callback.execute(redisTemplate);
            return List.of();
        }).when(redisTemplate).executePipelined(any(SessionCallback.class));

        cache.putAll(
                List.of(new ReviewSnapshotIdentity("uv-1", "word-1", "sense:sense-1", "vi")),
                Map.of("uv-1", snapshot),
                lookup.wordRevisions()
        );

        verify(valueOperations).set(
                eq("snapshot:word-1:sense:sense-1:vi:2"),
                anyString(),
                argThat((Duration ttl) -> !ttl.minus(Duration.ofHours(6)).isNegative()
                        && !Duration.ofHours(6).plusMinutes(30).minus(ttl).isNegative())
        );
        verify(redisTemplate).executePipelined(any(SessionCallback.class));
    }

    private ReviewVocabSnapshot snapshot() {
        return new ReviewVocabSnapshot(
                1, "word-1", "sense:sense-1", "vi", "bank", "noun", "bờ sông",
                null, List.of(), List.of(), Instant.parse("2026-08-08T00:00:00Z")
        );
    }
}
