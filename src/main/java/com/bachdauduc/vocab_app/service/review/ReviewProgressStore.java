package com.bachdauduc.vocab_app.service.review;

import com.bachdauduc.vocab_app.constant.ExerciseType;
import com.bachdauduc.vocab_app.properties.RedisKeyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ReviewProgressStore {
    private static final String RESERVE_SCRIPT_TEXT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local expires = tonumber(ARGV[2])
            local cleanup = tonumber(ARGV[3])
            redis.call('ZREMRANGEBYSCORE', key, '-inf', now)
            for index = 4, #ARGV do
                if redis.call('ZSCORE', key, ARGV[index]) == false then
                    redis.call('ZADD', key, expires, ARGV[index])
                    redis.call('EXPIRE', key, cleanup)
                    return ARGV[index]
                end
            end
            return nil
            """;

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisKeyProperties redisKeyProperties;
    private final Clock clock;
    private final DefaultRedisScript<String> reserveScript;

    @Autowired
    public ReviewProgressStore(
            RedisTemplate<String, String> redisTemplate,
            RedisKeyProperties redisKeyProperties
    ) {
        this(redisTemplate, redisKeyProperties, Clock.systemUTC());
    }

    ReviewProgressStore(
            RedisTemplate<String, String> redisTemplate,
            RedisKeyProperties redisKeyProperties,
            Clock clock
    ) {
        this.redisTemplate = redisTemplate;
        this.redisKeyProperties = redisKeyProperties;
        this.clock = clock;
        this.reserveScript = new DefaultRedisScript<>(RESERVE_SCRIPT_TEXT, String.class);
    }

    public Optional<ExerciseType> reserveFirstAvailable(
            String userId,
            String wordId,
            List<ExerciseType> candidates
    ) {
        List<ExerciseType> usableCandidates = new ArrayList<>(new LinkedHashSet<>(candidates))
                .stream()
                .filter(ExerciseType::isVocab)
                .toList();
        if (usableCandidates.isEmpty()) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        List<Object> arguments = new ArrayList<>();
        arguments.add(now.toEpochMilli());
        arguments.add(now.plus(redisKeyProperties.reviewProgressReservationTtl()).toEpochMilli());
        arguments.add(redisKeyProperties.reviewProgressCleanupTtl().toSeconds());
        usableCandidates.stream().map(Enum::name).forEach(arguments::add);

        String key = redisKeyProperties.reviewProgressKey(userId, wordId);
        try {
            String reserved = redisTemplate.execute(
                    reserveScript,
                    List.of(key),
                    arguments.toArray()
            );
            return Optional.ofNullable(reserved).map(ExerciseType::valueOf);
        } catch (RuntimeException exception) {
            log.warn("Redis review progress unavailable for wordId={}; using request-local fallback", wordId);
            return Optional.of(usableCandidates.getFirst());
        }
    }

    public void release(String userId, String wordId, ExerciseType type) {
        String key = redisKeyProperties.reviewProgressKey(userId, wordId);
        try {
            redisTemplate.opsForZSet().remove(key, type.name());
        } catch (RuntimeException exception) {
            log.warn("Could not release review progress reservation for wordId={}, type={}", wordId, type);
        }
    }
}
