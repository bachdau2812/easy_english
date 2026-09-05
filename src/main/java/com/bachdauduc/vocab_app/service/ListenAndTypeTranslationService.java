package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.entity.ListenAndTypeExerciseChallenge;
import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import com.bachdauduc.vocab_app.repository.ListenAndTypeExerciseChallengeRepository;
import com.bachdauduc.vocab_app.service.abstraction.GetTranslation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListenAndTypeTranslationService {
    private static final int MAX_BATCH_TEXTS = 100;
    // UTF-16 length is a conservative bound for Azure's 50,000-character limit.
    private static final int MAX_BATCH_CHARACTERS = 50_000;

    private final ListenAndTypeExerciseChallengeRepository challengeRepository;
    private final GetTranslation translator;
    // Bounded lock storage; concurrent requests for the same lesson share a lock in this instance.
    private final Object[] lessonLocks = IntStream.range(0, 64).mapToObj(i -> new Object()).toArray();

    public List<ListenAndTypeExerciseChallenge> loadTranslatedChallenges(String lessonId) {
        Object lock = lessonLocks[Math.floorMod(lessonId.hashCode(), lessonLocks.length)];
        synchronized (lock) {
            List<ListenAndTypeExerciseChallenge> challenges =
                    challengeRepository.findByListenExerciseIdOrderByPositionAsc(lessonId);
            Map<String, List<ListenAndTypeExerciseChallenge>> missingByContent = new LinkedHashMap<>();
            for (ListenAndTypeExerciseChallenge challenge : challenges) {
                if (!StringUtils.hasText(challenge.getTranslate()) && StringUtils.hasText(challenge.getContent())) {
                    if (challenge.getContent().length() > MAX_BATCH_CHARACTERS) {
                        log.warn("Skip oversized listening translation: challengeId={}", challenge.getId());
                        continue;
                    }
                    missingByContent.computeIfAbsent(challenge.getContent(), ignored -> new ArrayList<>()).add(challenge);
                }
            }
            if (missingByContent.isEmpty()) {
                return challenges;
            }

            List<String> batch = new ArrayList<>();
            int characters = 0;
            for (String content : missingByContent.keySet()) {
                if (batch.size() == MAX_BATCH_TEXTS || characters + content.length() > MAX_BATCH_CHARACTERS) {
                    translateAndSave(batch, missingByContent, lessonId);
                    batch = new ArrayList<>();
                    characters = 0;
                }
                batch.add(content);
                characters += content.length();
            }
            if (!batch.isEmpty()) {
                translateAndSave(batch, missingByContent, lessonId);
            }
            // Reload persisted values, including any translation saved by another application instance.
            return challengeRepository.findByListenExerciseIdOrderByPositionAsc(lessonId);
        }
    }

    private void translateAndSave(
            List<String> batch,
            Map<String, List<ListenAndTypeExerciseChallenge>> missingByContent,
            String lessonId
    ) {
        Map<String, String> translations;
        try {
            // No database transaction is held while waiting for Azure.
            translations = translator.translate(List.copyOf(batch), "vi");
        } catch (AppException exception) {
            if (exception.getErrorCode() != ErrorCode.TRANSLATION_FAILED) {
                throw exception;
            }
            log.warn("Listening translation batch failed: lessonId={}, textCount={}", lessonId, batch.size());
            return;
        }
        for (String content : batch) {
            String translation = translations.get(content);
            if (!StringUtils.hasText(translation)) {
                continue;
            }
            for (ListenAndTypeExerciseChallenge challenge : missingByContent.get(content)) {
                challengeRepository.saveTranslationIfMissing(challenge.getId(), content, translation);
            }
        }
    }
}
