package com.bachdauduc.vocab_app.service.review;

import com.bachdauduc.vocab_app.properties.RedisKeyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewVocabCacheRevisionService {
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisKeyProperties redisKeyProperties;

    public void invalidateAfterCommit(Collection<String> wordIds) {
        Set<String> distinctWordIds = wordIds == null
                ? Set.of()
                : wordIds.stream()
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (distinctWordIds.isEmpty()) {
            return;
        }

        Runnable invalidation = () -> incrementRevisions(distinctWordIds);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidation.run();
                }
            });
            return;
        }
        invalidation.run();
    }

    private void incrementRevisions(Set<String> wordIds) {
        try {
            wordIds.forEach(wordId -> redisTemplate.opsForValue()
                    .increment(redisKeyProperties.reviewVocabRevisionKey(wordId)));
        } catch (RuntimeException exception) {
            log.warn("Review snapshot revision invalidation failed; snapshots remain bounded by TTL", exception);
        }
    }
}
