package com.bachdauduc.vocab_app.service.review;

import com.bachdauduc.vocab_app.constant.ExerciseType;
import com.bachdauduc.vocab_app.properties.RedisKeyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static com.bachdauduc.vocab_app.constant.ExerciseType.VOCAB_FILL_MISSING_WORD_PART;
import static com.bachdauduc.vocab_app.constant.ExerciseType.VOCAB_WORD_TO_MEANING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewProgressStoreTest {
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock RedisKeyProperties redisKeyProperties;

    ReviewProgressStore store;

    @BeforeEach
    void setUp() {
        when(redisKeyProperties.reviewProgressKey("user-1", "word-1"))
                .thenReturn("review-progress:user-1:word-1");
        store = new ReviewProgressStore(
                redisTemplate,
                redisKeyProperties,
                Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void reservesFirstAvailableCandidateThroughOneLuaExecution() {
        stubTtls();
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenReturn(VOCAB_FILL_MISSING_WORD_PART.name());

        assertThat(store.reserveFirstAvailable(
                "user-1",
                "word-1",
                List.of(VOCAB_WORD_TO_MEANING, VOCAB_FILL_MISSING_WORD_PART)
        )).contains(VOCAB_FILL_MISSING_WORD_PART);

        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of("review-progress:user-1:word-1")),
                any(Object[].class)
        );
    }

    @Test
    void returnsEmptyWhenAllCandidatesAreReserved() {
        stubTtls();
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenReturn(null);

        assertThat(store.reserveFirstAvailable(
                "user-1", "word-1", List.of(VOCAB_WORD_TO_MEANING)
        )).isEmpty();
    }

    @Test
    void fallsBackToFirstCandidateWhenRedisIsUnavailable() {
        stubTtls();
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                any(Object[].class)
        )).thenThrow(new RedisConnectionFailureException("offline"));

        assertThat(store.reserveFirstAvailable(
                "user-1",
                "word-1",
                List.of(VOCAB_WORD_TO_MEANING, VOCAB_FILL_MISSING_WORD_PART)
        )).contains(VOCAB_WORD_TO_MEANING);
    }

    @Test
    void releasesUnexpectedFailureReservation() {
        store.release("user-1", "word-1", VOCAB_WORD_TO_MEANING);

        verify(redisTemplate).opsForZSet();
    }

    private void stubTtls() {
        when(redisKeyProperties.reviewProgressReservationTtl()).thenReturn(Duration.ofHours(2));
        when(redisKeyProperties.reviewProgressCleanupTtl()).thenReturn(Duration.ofHours(3));
    }
}
