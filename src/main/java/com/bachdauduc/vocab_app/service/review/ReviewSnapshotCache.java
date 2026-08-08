package com.bachdauduc.vocab_app.service.review;

import com.bachdauduc.vocab_app.properties.RedisKeyProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

@Slf4j
@Service
public class ReviewSnapshotCache {
    private static final Duration DEFAULT_TTL = Duration.ofHours(6);
    private static final Duration DEFAULT_JITTER = Duration.ofMinutes(30);

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisKeyProperties redisKeyProperties;
    private final ObjectMapper objectMapper;
    private final RandomGenerator random;
    private final boolean enabled;

    @Autowired
    public ReviewSnapshotCache(
            RedisTemplate<String, String> redisTemplate,
            RedisKeyProperties redisKeyProperties,
            ObjectMapper objectMapper,
            @Value("${review.snapshot-cache.enabled:true}") boolean enabled
    ) {
        this(redisTemplate, redisKeyProperties, objectMapper, new java.util.Random(), enabled);
    }

    ReviewSnapshotCache(
            RedisTemplate<String, String> redisTemplate,
            RedisKeyProperties redisKeyProperties,
            ObjectMapper objectMapper,
            RandomGenerator random,
            boolean enabled
    ) {
        this.redisTemplate = redisTemplate;
        this.redisKeyProperties = redisKeyProperties;
        this.objectMapper = objectMapper;
        this.random = random;
        this.enabled = enabled;
    }

    public ReviewSnapshotLookup lookup(List<ReviewSnapshotIdentity> identities) {
        if (identities == null || identities.isEmpty()) {
            return new ReviewSnapshotLookup(Map.of(), Map.of());
        }

        Map<String, Long> defaultRevisions = defaultRevisions(identities);
        if (!enabled) {
            return new ReviewSnapshotLookup(Map.of(), defaultRevisions);
        }

        try {
            ValueOperations<String, String> values = redisTemplate.opsForValue();
            Map<String, Long> revisions = readRevisions(values, identities);
            List<String> snapshotKeys = identities.stream()
                    .map(identity -> snapshotKey(identity, revisions.getOrDefault(identity.wordId(), 0L)))
                    .toList();
            List<String> serializedSnapshots = values.multiGet(snapshotKeys);
            Map<String, ReviewVocabSnapshot> hits = new LinkedHashMap<>();
            for (int index = 0; index < identities.size(); index++) {
                String serialized = valueAt(serializedSnapshots, index);
                ReviewVocabSnapshot snapshot = deserialize(serialized);
                ReviewSnapshotIdentity identity = identities.get(index);
                if (matches(snapshot, identity)) {
                    hits.put(identity.userVocabId(), snapshot);
                }
            }
            return new ReviewSnapshotLookup(hits, revisions);
        } catch (RuntimeException exception) {
            log.warn("Review snapshot cache read failed; using database fallback", exception);
            return new ReviewSnapshotLookup(Map.of(), defaultRevisions);
        }
    }

    public void putAll(
            List<ReviewSnapshotIdentity> identities,
            Map<String, ReviewVocabSnapshot> snapshotsByUserVocabId,
            Map<String, Long> wordRevisions
    ) {
        if (!enabled || identities == null || identities.isEmpty() || snapshotsByUserVocabId == null) {
            return;
        }

        try {
            Set<String> writtenKeys = new LinkedHashSet<>();
            List<SnapshotWrite> writes = new ArrayList<>();
            for (ReviewSnapshotIdentity identity : identities) {
                ReviewVocabSnapshot snapshot = snapshotsByUserVocabId.get(identity.userVocabId());
                if (!matches(snapshot, identity)) {
                    continue;
                }
                long revision = wordRevisions.getOrDefault(identity.wordId(), 0L);
                String key = snapshotKey(identity, revision);
                if (!writtenKeys.add(key)) {
                    continue;
                }
                writes.add(new SnapshotWrite(
                        key,
                        objectMapper.writeValueAsString(snapshot),
                        ttlWithJitter()
                ));
            }
            if (!writes.isEmpty()) {
                redisTemplate.executePipelined(new SessionCallback<>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public <K, V> Object execute(RedisOperations<K, V> operations) {
                        RedisOperations<String, String> stringOperations =
                                (RedisOperations<String, String>) operations;
                        writes.forEach(write -> stringOperations.opsForValue()
                                .set(write.key(), write.value(), write.ttl()));
                        return null;
                    }
                });
            }
        } catch (Exception exception) {
            log.warn("Review snapshot cache write failed; response remains usable", exception);
        }
    }

    private Map<String, Long> readRevisions(
            ValueOperations<String, String> values,
            List<ReviewSnapshotIdentity> identities
    ) {
        List<String> wordIds = new ArrayList<>(identities.stream()
                .map(ReviewSnapshotIdentity::wordId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        List<String> revisionKeys = wordIds.stream()
                .map(redisKeyProperties::reviewVocabRevisionKey)
                .toList();
        List<String> revisionValues = values.multiGet(revisionKeys);
        Map<String, Long> revisions = new LinkedHashMap<>();
        for (int index = 0; index < wordIds.size(); index++) {
            revisions.put(wordIds.get(index), parseRevision(valueAt(revisionValues, index)));
        }
        return revisions;
    }

    private Map<String, Long> defaultRevisions(List<ReviewSnapshotIdentity> identities) {
        Map<String, Long> revisions = new LinkedHashMap<>();
        identities.forEach(identity -> revisions.putIfAbsent(identity.wordId(), 0L));
        return revisions;
    }

    private String snapshotKey(ReviewSnapshotIdentity identity, long revision) {
        return redisKeyProperties.reviewVocabSnapshotKey(
                identity.wordId(), identity.senseKey(), identity.langCode(), revision);
    }

    private ReviewVocabSnapshot deserialize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, ReviewVocabSnapshot.class);
        } catch (Exception exception) {
            log.warn("Invalid review snapshot ignored");
            return null;
        }
    }

    private boolean matches(ReviewVocabSnapshot snapshot, ReviewSnapshotIdentity identity) {
        return snapshot != null
                && snapshot.schemaVersion() == ReviewVocabSnapshot.CURRENT_SCHEMA_VERSION
                && identity.wordId().equals(snapshot.wordId())
                && identity.senseKey().equals(snapshot.senseKey())
                && Objects.equals(identity.langCode(), snapshot.langCode());
    }

    private long parseRevision(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private String valueAt(List<String> values, int index) {
        return values != null && index < values.size() ? values.get(index) : null;
    }

    private Duration ttlWithJitter() {
        Duration base = redisKeyProperties.reviewVocabSnapshotTtl();
        Duration jitter = redisKeyProperties.reviewVocabSnapshotJitter();
        base = base == null ? DEFAULT_TTL : base;
        jitter = jitter == null ? DEFAULT_JITTER : jitter;
        long jitterSeconds = Math.max(jitter.toSeconds(), 0);
        return base.plusSeconds(jitterSeconds == 0 ? 0 : random.nextLong(jitterSeconds + 1));
    }

    private record SnapshotWrite(String key, String value, Duration ttl) {
    }
}
