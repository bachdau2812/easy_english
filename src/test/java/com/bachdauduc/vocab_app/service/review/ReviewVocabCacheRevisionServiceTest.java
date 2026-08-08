package com.bachdauduc.vocab_app.service.review;

import com.bachdauduc.vocab_app.properties.RedisKeyProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewVocabCacheRevisionServiceTest {
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock RedisKeyProperties redisKeyProperties;

    @Test
    void incrementsDistinctWordRevisionsImmediatelyOutsideTransaction() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisKeyProperties.reviewVocabRevisionKey("word-1")).thenReturn("revision:word-1");

        new ReviewVocabCacheRevisionService(redisTemplate, redisKeyProperties)
                .invalidateAfterCommit(List.of("word-1", "word-1"));

        verify(valueOperations).increment("revision:word-1");
    }
}
