package com.bachdauduc.vocab_app.service.review;

import com.bachdauduc.vocab_app.dto.response.worddata.WordSenseResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordSoundResponse;
import com.bachdauduc.vocab_app.entity.UserVocabulary;
import com.bachdauduc.vocab_app.entity.Word;
import com.bachdauduc.vocab_app.entity.WordExample;
import com.bachdauduc.vocab_app.entity.WordExampleLocalization;
import com.bachdauduc.vocab_app.entity.WordSense;
import com.bachdauduc.vocab_app.entity.WordSenseLocalization;
import com.bachdauduc.vocab_app.entity.WordSound;
import com.bachdauduc.vocab_app.repository.WordExampleLocalizationRepository;
import com.bachdauduc.vocab_app.repository.WordExampleRepository;
import com.bachdauduc.vocab_app.repository.WordRepository;
import com.bachdauduc.vocab_app.repository.WordSenseLocalizationRepository;
import com.bachdauduc.vocab_app.repository.WordSenseRepository;
import com.bachdauduc.vocab_app.repository.WordSoundRepository;
import com.bachdauduc.vocab_app.utils.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewVocabDataLoader {
    private static final String SOURCE_MOCHI = "MOCHI";

    private final WordRepository wordRepository;
    private final WordSenseRepository wordSenseRepository;
    private final WordSenseLocalizationRepository wordSenseLocalizationRepository;
    private final WordSoundRepository wordSoundRepository;
    private final WordExampleRepository wordExampleRepository;
    private final WordExampleLocalizationRepository wordExampleLocalizationRepository;
    private final ReviewSnapshotCache snapshotCache;

    public Map<String, ReviewVocabSnapshot> load(List<UserVocabulary> vocabularies, String langCode) {
        if (vocabularies == null || vocabularies.isEmpty()) {
            return Map.of();
        }

        String normalizedLangCode = StringUtils.hasText(langCode) ? langCode.trim().toLowerCase() : "";
        List<ReviewSnapshotIdentity> identities = vocabularies.stream()
                .filter(this::hasSnapshotIdentity)
                .map(vocabulary -> identity(vocabulary, normalizedLangCode))
                .toList();
        ReviewSnapshotLookup lookup = snapshotCache.lookup(identities);
        Map<String, ReviewVocabSnapshot> result = new LinkedHashMap<>(lookup.hits());
        List<UserVocabulary> misses = vocabularies.stream()
                .filter(this::hasSnapshotIdentity)
                .filter(vocabulary -> !result.containsKey(vocabulary.getId()))
                .toList();
        if (misses.isEmpty()) {
            return orderedResult(vocabularies, result);
        }

        Map<String, ReviewVocabSnapshot> loaded = loadMisses(misses, normalizedLangCode);
        result.putAll(loaded);
        Set<String> missIds = misses.stream().map(UserVocabulary::getId).collect(Collectors.toSet());
        List<ReviewSnapshotIdentity> missIdentities = identities.stream()
                .filter(identity -> missIds.contains(identity.userVocabId()))
                .toList();
        snapshotCache.putAll(missIdentities, loaded, lookup.wordRevisions());
        return orderedResult(vocabularies, result);
    }

    private Map<String, ReviewVocabSnapshot> loadMisses(
            List<UserVocabulary> vocabularies,
            String langCode
    ) {
        Set<String> wordIds = values(vocabularies, UserVocabulary::getWordId);
        Set<String> senseIds = values(vocabularies, UserVocabulary::getSenseId);
        Set<String> localizedSenseIds = values(vocabularies, UserVocabulary::getSenseLocalizedId);
        Set<String> exampleSenseIds = new LinkedHashSet<>(senseIds);
        exampleSenseIds.addAll(localizedSenseIds);

        Map<String, Word> words = byId(wordRepository.findAllById(wordIds), Word::getId);
        Map<String, WordSense> senses = byId(wordSenseRepository.findAllById(senseIds), WordSense::getId);
        Map<String, WordSenseLocalization> localizedSenses = byId(
                wordSenseLocalizationRepository.findAllById(localizedSenseIds),
                WordSenseLocalization::getId
        );
        Map<String, WordSenseLocalization> translationsBySense =
                firstLocalizationBySense(senseIds, langCode);
        Map<String, List<WordSound>> soundsByWord = group(
                wordSoundRepository.findByWordIdIn(wordIds), WordSound::getWordId);
        Map<String, List<WordExample>> examplesBySense = group(
                wordExampleRepository.findBySenseIdIn(exampleSenseIds), WordExample::getSenseId);
        Set<String> exampleIds = examplesBySense.values().stream()
                .flatMap(Collection::stream)
                .map(WordExample::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, WordExampleLocalization> exampleTranslations = byExampleId(
                exampleIds.isEmpty()
                        ? List.of()
                        : wordExampleLocalizationRepository.findByExampleIdInAndLangCode(exampleIds, langCode)
        );

        Map<String, ReviewVocabSnapshot> loaded = new LinkedHashMap<>();
        for (UserVocabulary vocabulary : vocabularies) {
            Word word = words.get(vocabulary.getWordId());
            if (word == null) {
                continue;
            }
            ReviewVocabSnapshot snapshot = buildSnapshot(
                    vocabulary,
                    word,
                    valueByTextKey(senses, vocabulary.getSenseId()),
                    valueByTextKey(localizedSenses, vocabulary.getSenseLocalizedId()),
                    valueByTextKey(translationsBySense, vocabulary.getSenseId()),
                    soundsByWord.getOrDefault(vocabulary.getWordId(), List.of()),
                    examplesBySense.getOrDefault(exampleSenseId(vocabulary), List.of()),
                    exampleTranslations,
                    langCode
            );
            if (snapshot != null) {
                loaded.put(vocabulary.getId(), snapshot);
            }
        }
        return loaded;
    }

    private ReviewVocabSnapshot buildSnapshot(
            UserVocabulary vocabulary,
            Word word,
            WordSense sense,
            WordSenseLocalization localizedSense,
            WordSenseLocalization translation,
            List<WordSound> sounds,
            List<WordExample> examples,
            Map<String, WordExampleLocalization> exampleTranslations,
            String langCode
    ) {
        boolean localized = StringUtils.hasText(vocabulary.getSenseLocalizedId());
        if ((localized && localizedSense == null) || (!localized && sense == null)) {
            return null;
        }

        String meaning = localized
                ? firstText(localizedSense.getShortMeaning(), localizedSense.getFullLocalizedDefinition())
                : translation != null
                ? firstText(translation.getShortMeaning(), translation.getFullLocalizedDefinition())
                : sense.getDefinition();
        WordSenseResponse wordSense = localized
                ? localizedSenseResponse(word, localizedSense)
                : standardSenseResponse(word, sense, translation);

        List<WordSoundResponse> soundResponses = sounds.stream()
                .sorted(Comparator.comparing(sound -> !SOURCE_MOCHI.equalsIgnoreCase(sound.getSoundSource())))
                .map(this::soundResponse)
                .toList();
        List<ReviewExample> reviewExamples = examples.stream()
                .filter(example -> StringUtils.hasText(example.getText()))
                .map(example -> new ReviewExample(
                        example.getId(),
                        example.getText(),
                        exampleTranslations.containsKey(example.getId())
                                ? exampleTranslations.get(example.getId()).getTranslatedText()
                                : null
                ))
                .toList();

        return new ReviewVocabSnapshot(
                ReviewVocabSnapshot.CURRENT_SCHEMA_VERSION,
                word.getId(),
                senseKey(vocabulary),
                langCode,
                word.getWord(),
                word.getPos(),
                meaning,
                wordSense,
                soundResponses,
                reviewExamples,
                Instant.now()
        );
    }

    private Map<String, WordSenseLocalization> firstLocalizationBySense(
            Set<String> senseIds,
            String langCode
    ) {
        if (senseIds.isEmpty() || !StringUtils.hasText(langCode)) {
            return Map.of();
        }
        return wordSenseLocalizationRepository.findBySenseIdInAndLangCode(senseIds, langCode)
                .stream()
                .filter(localization -> StringUtils.hasText(localization.getSenseId()))
                .collect(Collectors.toMap(
                        WordSenseLocalization::getSenseId,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
    }

    private WordSenseResponse localizedSenseResponse(Word word, WordSenseLocalization localization) {
        return WordSenseResponse.builder()
                .senseId(localization.getSenseId())
                .localizationId(localization.getId())
                .wordId(word.getId())
                .word(word.getWord())
                .pos(word.getPos())
                .certLevel(word.getCertLevel())
                .shortMeaning(localization.getShortMeaning())
                .examples(List.of())
                .synonyms(List.of())
                .antonyms(List.of())
                .derived(List.of())
                .coordinateTerms(List.of())
                .formOf(List.of())
                .altOf(List.of())
                .trans(translation(localization))
                .build();
    }

    private WordSenseResponse standardSenseResponse(
            Word word,
            WordSense sense,
            WordSenseLocalization localization
    ) {
        return WordSenseResponse.builder()
                .senseId(sense.getId())
                .localizationId(localization != null ? localization.getId() : null)
                .wordId(word.getId())
                .word(word.getWord())
                .pos(word.getPos())
                .certLevel(word.getCertLevel())
                .shortMeaning(localization != null ? localization.getShortMeaning() : null)
                .definition(sense.getDefinition())
                .synonyms(RedisUtil.deserializeList(sense.getSynonyms(), String.class))
                .antonyms(RedisUtil.deserializeList(sense.getAntonyms(), String.class))
                .examples(List.of())
                .trans(localization != null ? translation(localization) : null)
                .derived(RedisUtil.deserializeList(sense.getDerived(), String.class))
                .coordinateTerms(RedisUtil.deserializeList(sense.getCoordinateTerms(), String.class))
                .formOf(RedisUtil.deserializeList(sense.getFormOf(), String.class))
                .altOf(RedisUtil.deserializeList(sense.getAltOf(), String.class))
                .build();
    }

    private WordSenseResponse.Translation translation(WordSenseLocalization localization) {
        if (localization == null
                || (!StringUtils.hasText(localization.getShortMeaning())
                && !StringUtils.hasText(localization.getFullLocalizedDefinition()))) {
            return null;
        }
        return WordSenseResponse.Translation.builder()
                .langCode(localization.getLangCode())
                .shortMeaning(localization.getShortMeaning())
                .definition(localization.getFullLocalizedDefinition())
                .build();
    }

    private WordSoundResponse soundResponse(WordSound sound) {
        return WordSoundResponse.builder()
                .wordId(sound.getWordId())
                .ipa(sound.getIpa())
                .tags(RedisUtil.deserializeList(sound.getTags(), String.class))
                .soundSource(sound.getSoundSource())
                .oggUrl(sound.getOggUrl())
                .mp3Url(sound.getMp3Url())
                .enpr(sound.getEnpr())
                .build();
    }

    private List<ReviewSnapshotIdentity> identities(
            List<UserVocabulary> vocabularies,
            String langCode
    ) {
        return vocabularies.stream().map(vocabulary -> identity(vocabulary, langCode)).toList();
    }

    private ReviewSnapshotIdentity identity(UserVocabulary vocabulary, String langCode) {
        return new ReviewSnapshotIdentity(
                vocabulary.getId(), vocabulary.getWordId(), senseKey(vocabulary), langCode);
    }

    private String senseKey(UserVocabulary vocabulary) {
        return StringUtils.hasText(vocabulary.getSenseLocalizedId())
                ? "localized:" + vocabulary.getSenseLocalizedId()
                : "sense:" + vocabulary.getSenseId();
    }

    private String exampleSenseId(UserVocabulary vocabulary) {
        return StringUtils.hasText(vocabulary.getSenseLocalizedId())
                ? vocabulary.getSenseLocalizedId()
                : vocabulary.getSenseId();
    }

    private boolean hasSnapshotIdentity(UserVocabulary vocabulary) {
        return vocabulary != null
                && StringUtils.hasText(vocabulary.getId())
                && StringUtils.hasText(vocabulary.getWordId())
                && (StringUtils.hasText(vocabulary.getSenseId())
                || StringUtils.hasText(vocabulary.getSenseLocalizedId()));
    }

    private <T> T valueByTextKey(Map<String, T> values, String key) {
        return StringUtils.hasText(key) ? values.get(key) : null;
    }

    private <T> Set<String> values(List<UserVocabulary> vocabularies, Function<UserVocabulary, String> mapper) {
        return vocabularies.stream()
                .map(mapper)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private <T> Map<String, T> byId(Iterable<T> values, Function<T, String> id) {
        Map<String, T> result = new LinkedHashMap<>();
        values.forEach(value -> result.putIfAbsent(id.apply(value), value));
        return result;
    }

    private <T> Map<String, List<T>> group(Collection<T> values, Function<T, String> key) {
        return values.stream().collect(Collectors.groupingBy(
                key,
                LinkedHashMap::new,
                Collectors.toCollection(ArrayList::new)
        ));
    }

    private Map<String, WordExampleLocalization> byExampleId(
            Collection<WordExampleLocalization> localizations
    ) {
        return localizations.stream().collect(Collectors.toMap(
                WordExampleLocalization::getExampleId,
                Function.identity(),
                (first, ignored) -> first,
                LinkedHashMap::new
        ));
    }

    private Map<String, ReviewVocabSnapshot> orderedResult(
            List<UserVocabulary> vocabularies,
            Map<String, ReviewVocabSnapshot> snapshots
    ) {
        Map<String, ReviewVocabSnapshot> ordered = new LinkedHashMap<>();
        vocabularies.forEach(vocabulary -> {
            ReviewVocabSnapshot snapshot = snapshots.get(vocabulary.getId());
            if (snapshot != null) {
                ordered.put(vocabulary.getId(), snapshot);
            }
        });
        return Map.copyOf(ordered);
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}
