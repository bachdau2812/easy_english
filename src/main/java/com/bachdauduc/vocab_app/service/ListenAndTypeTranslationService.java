package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.entity.ListenAndTypeExerciseChallenge;
import com.bachdauduc.vocab_app.entity.ListenExercise;
import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import com.bachdauduc.vocab_app.repository.ListenAndTypeExerciseChallengeRepository;
import com.bachdauduc.vocab_app.repository.ListenExerciseRepository;
import com.bachdauduc.vocab_app.service.abstraction.GetTranslation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Entities;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListenAndTypeTranslationService {
    // UTF-16 length, including escaped HTML and markers, conservatively bounds Azure's limit.
    private static final int MAX_DOCUMENT_CHARACTERS = 50_000;
    private static final Pattern DOCUMENT_LINE_SEPARATOR =
            Pattern.compile("\\R|\\\\r\\\\n|\\\\n|\\\\r");
    private static final Pattern SEGMENT_MARKER = Pattern.compile("\\[\\[SEG_[^\\]]*\\]\\]");

    private final ListenAndTypeExerciseChallengeRepository challengeRepository;
    private final ListenExerciseRepository lessonRepository;
    private final GetTranslation translator;
    // Concurrent requests for the same lesson share a lock in this application instance.
    private final Object[] lessonLocks = IntStream.range(0, 64).mapToObj(i -> new Object()).toArray();

    public List<ListenAndTypeExerciseChallenge> loadTranslatedChallenges(String lessonId) {
        Object lock = lessonLocks[Math.floorMod(lessonId.hashCode(), lessonLocks.length)];
        synchronized (lock) {
            List<ListenAndTypeExerciseChallenge> challenges =
                    challengeRepository.findByListenExerciseIdOrderByPositionAsc(lessonId);
            if (challenges.stream().noneMatch(challenge -> !StringUtils.hasText(challenge.getTranslate()))) {
                return challenges;
            }

            String document = lessonRepository.findById(lessonId)
                    .map(ListenExercise::getFullDocument).orElse(null);
            LinkedHashMap<Integer, Segment> segments = splitDocument(document, challenges);
            if (segments.isEmpty()) {
                log.warn("Skip unaligned listening translation document: lessonId={}", lessonId);
                return challenges;
            }

            String html = buildHtml(segments);
            if (html.length() > MAX_DOCUMENT_CHARACTERS) {
                log.warn("Skip oversized listening translation document: lessonId={}", lessonId);
                return challenges;
            }

            try {
                // Preserve all dialogue context, even when only one challenge needs a translation.
                // No database transaction is held while waiting for Azure.
                String translatedHtml = translator.translateHtml(html, "vi");
                Map<Integer, String> translations = parseTranslations(translatedHtml, segments);
                if (translations.isEmpty()) {
                    log.warn("Invalid listening translation markers: lessonId={}", lessonId);
                } else {
                    for (var entry : segments.entrySet()) {
                        ListenAndTypeExerciseChallenge challenge = entry.getValue().challenge();
                        String translation = translations.get(entry.getKey());
                        if (!StringUtils.hasText(challenge.getTranslate()) && StringUtils.hasText(translation)) {
                            challengeRepository.saveTranslationIfMissing(
                                    challenge.getId(), challenge.getContent(), translation);
                        }
                    }
                }
            } catch (AppException exception) {
                if (exception.getErrorCode() != ErrorCode.TRANSLATION_FAILED) {
                    throw exception;
                }
                log.warn("Listening document translation failed: lessonId={}", lessonId);
            }
            // Reload persisted values, including any translation saved by another application instance.
            return challengeRepository.findByListenExerciseIdOrderByPositionAsc(lessonId);
        }
    }

    private LinkedHashMap<Integer, Segment> splitDocument(
            String document, List<ListenAndTypeExerciseChallenge> challenges) {
        LinkedHashMap<Integer, Segment> segments = new LinkedHashMap<>();
        if (!StringUtils.hasText(document)) {
            log.warn("Cannot split listening document: reason=missing-document");
            return segments;
        }
        if (document.contains("[[SEG_")) {
            log.warn("Cannot split listening document: reason=reserved-marker");
            return segments;
        }
        // Keep interior empty lines so they cannot silently shift subsequent challenge positions.
        // Support both real line separators and legacy data containing literal escaped \n text.
        // Pattern.split discards trailing line delimiters.
        String[] lines = DOCUMENT_LINE_SEPARATOR.split(document);
        if (lines.length != challenges.size()) {
            log.warn("Cannot split listening document: reason=count-mismatch, segmentCount={}, challengeCount={}",
                    lines.length, challenges.size());
            return segments;
        }
        for (int i = 0; i < lines.length; i++) {
            ListenAndTypeExerciseChallenge challenge = challenges.get(i);
            Integer position = challenge.getPosition();
            if (position == null || segments.containsKey(position) || !StringUtils.hasText(lines[i])) {
                log.warn("Cannot split listening document: reason=invalid-segment, ordinal={}, position={}, blank={}",
                        i + 1, position, !StringUtils.hasText(lines[i]));
                return new LinkedHashMap<>();
            }
            // Use the actual ordered positions, which need not start at one or be contiguous.
            segments.put(position, new Segment(challenge, lines[i]));
        }
        return segments;
    }

    private String buildHtml(LinkedHashMap<Integer, Segment> segments) {
        StringBuilder html = new StringBuilder();
        int ordinal = 1;
        for (Segment segment : segments.values()) {
            if (!html.isEmpty()) {
                html.append(' ');
            }
            html.append(Entities.escape(segment.source()))
                    .append(" <span translate=\"no\">")
                    .append(marker(ordinal++))
                    .append("</span>");
        }
        return html.toString();
    }

    private Map<Integer, String> parseTranslations(
            String translatedHtml, LinkedHashMap<Integer, Segment> segments) {
        if (!StringUtils.hasText(translatedHtml)) {
            return Map.of();
        }
        // Parse HTML before extracting markers: decode entities and discard provider HTML wrappers.
        String text = Jsoup.parseBodyFragment(translatedHtml).body().text();
        var matcher = SEGMENT_MARKER.matcher(text);
        Map<Integer, String> translations = new LinkedHashMap<>();
        int start = 0;
        int ordinal = 1;
        for (Integer position : segments.keySet()) {
            if (!matcher.find() || !matcher.group().equals(marker(ordinal++))) {
                return Map.of();
            }
            String translation = text.substring(start, matcher.start()).strip();
            if (translation.contains("[[SEG_")) {
                return Map.of();
            }
            translations.put(position, translation);
            start = matcher.end();
        }
        // Validate the entire document before allowing any persistence.
        if (!text.substring(start).isBlank()) {
            return Map.of();
        }
        return translations;
    }

    private String marker(int ordinal) {
        return "[[SEG_" + ordinal + "]]";
    }

    private record Segment(ListenAndTypeExerciseChallenge challenge, String source) {
    }
}
