package com.bachdauduc.vocab_app.service.review;

import com.bachdauduc.vocab_app.dto.response.worddata.WordSenseResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordSoundResponse;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record ReviewVocabSnapshot(
        int schemaVersion,
        String wordId,
        String senseKey,
        String langCode,
        String word,
        String pos,
        String meaning,
        WordSenseResponse wordSense,
        List<WordSoundResponse> sounds,
        List<ReviewExample> examples,
        Instant generatedAt,
        List<String> forms
) {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    public ReviewVocabSnapshot {
        sounds = sounds == null ? List.of() : List.copyOf(sounds);
        examples = examples == null ? List.of() : List.copyOf(examples);
        forms = forms == null ? List.of() : List.copyOf(forms);
    }

    public ReviewVocabSnapshot(int schemaVersion, String wordId, String senseKey, String langCode,
                               String word, String pos, String meaning, WordSenseResponse wordSense,
                               List<WordSoundResponse> sounds, List<ReviewExample> examples, Instant generatedAt) {
        this(schemaVersion, wordId, senseKey, langCode, word, pos, meaning, wordSense, sounds, examples,
                generatedAt, List.of());
    }

    public Optional<String> playableSoundUrl() {
        return sounds.stream()
                .filter(sound -> "MOCHI".equalsIgnoreCase(sound.getSoundSource()))
                .map(this::soundUrl)
                .filter(StringUtils::hasText)
                .findFirst()
                .or(() -> sounds.stream()
                        .map(this::soundUrl)
                        .filter(StringUtils::hasText)
                        .findFirst());
    }

    public Optional<WordSoundResponse> preferredSound() {
        return sounds.stream()
                .filter(sound -> "MOCHI".equalsIgnoreCase(sound.getSoundSource()))
                .findFirst()
                .or(() -> sounds.stream().findFirst());
    }

    private String soundUrl(WordSoundResponse sound) {
        return StringUtils.hasText(sound.getMp3Url()) ? sound.getMp3Url() : sound.getOggUrl();
    }
}
