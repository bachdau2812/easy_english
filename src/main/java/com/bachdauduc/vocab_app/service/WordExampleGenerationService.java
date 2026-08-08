package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.entity.UserVocabulary;
import com.bachdauduc.vocab_app.entity.Word;
import com.bachdauduc.vocab_app.entity.WordExample;
import com.bachdauduc.vocab_app.entity.WordSense;
import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.repository.WordExampleRepository;
import com.bachdauduc.vocab_app.repository.WordRepository;
import com.bachdauduc.vocab_app.repository.WordSenseRepository;
import com.bachdauduc.vocab_app.service.abstraction.WordExampleGenerator;
import com.bachdauduc.vocab_app.service.model.GeneratedWordExample;
import com.bachdauduc.vocab_app.service.model.WordExampleGenerationInput;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WordExampleGenerationService {
    private static final int MINIMUM_EXAMPLES_PER_SENSE = 4;

    WordRepository wordRepository;
    WordSenseRepository wordSenseRepository;
    WordExampleRepository wordExampleRepository;
    WordExampleGenerator wordExampleGenerator;
    GeneratedWordExamplePersistenceService persistenceService;

    public void ensureExamples(List<UserVocabulary> vocabularies) {
        if (vocabularies == null || vocabularies.isEmpty()) {
            return;
        }

        Map<ExampleKey, UserVocabulary> vocabByKey = vocabularies.stream()
                .filter(this::hasStandardSense)
                .collect(Collectors.toMap(
                        vocab -> new ExampleKey(vocab.getWordId(), vocab.getSenseId()),
                        Function.identity(),
                        (first, duplicate) -> first,
                        LinkedHashMap::new
                ));
        if (vocabByKey.isEmpty()) {
            return;
        }

        Map<String, Word> wordsById = wordRepository.findAllById(
                        vocabByKey.keySet().stream().map(ExampleKey::wordId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Word::getId, Function.identity()));
        Map<String, WordSense> sensesById = wordSenseRepository.findAllById(
                        vocabByKey.keySet().stream().map(ExampleKey::senseId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(WordSense::getId, Function.identity()));

        Set<String> senseIds = vocabByKey.keySet().stream()
                .map(ExampleKey::senseId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<ExampleKey, Set<String>> existingByKey = wordExampleRepository.findBySenseIdIn(senseIds)
                .stream()
                .filter(example -> StringUtils.hasText(example.getWordId()))
                .filter(example -> StringUtils.hasText(example.getSenseId()))
                .filter(example -> StringUtils.hasText(example.getText()))
                .collect(Collectors.groupingBy(
                        example -> new ExampleKey(example.getWordId(), example.getSenseId()),
                        LinkedHashMap::new,
                        Collectors.mapping(
                                example -> normalizeSentence(example.getText()),
                                Collectors.toCollection(LinkedHashSet::new)
                        )
                ));

        List<WordExampleGenerationInput> inputs = vocabByKey.keySet().stream()
                .map(key -> toGenerationInput(key, wordsById.get(key.wordId()), sensesById.get(key.senseId()),
                        existingByKey.getOrDefault(key, Set.of()).size()))
                .filter(java.util.Objects::nonNull)
                .toList();
        if (inputs.isEmpty()) {
            return;
        }

        try {
            List<GeneratedWordExample> generatedExamples = wordExampleGenerator.generate(inputs);
            if (!generatedExamples.isEmpty()) {
                persistenceService.persist(generatedExamples);
            }
            log.info("Word example generation preflight completed: requestedSenseCount={}, generatedExampleCount={}",
                    inputs.size(), generatedExamples.size());
        } catch (AppException exception) {
            log.warn("Word example generation preflight skipped: requestedSenseCount={}, errorCode={}",
                    inputs.size(), exception.getErrorCode());
        }
    }

    private WordExampleGenerationInput toGenerationInput(
            ExampleKey key,
            Word word,
            WordSense sense,
            int existingCount
    ) {
        if (existingCount >= MINIMUM_EXAMPLES_PER_SENSE
                || word == null
                || sense == null
                || !key.wordId().equals(sense.getWordId())
                || !StringUtils.hasText(word.getWord())
                || !StringUtils.hasText(sense.getDefinition())) {
            return null;
        }
        return new WordExampleGenerationInput(
                UUID.randomUUID().toString(),
                key.wordId(),
                key.senseId(),
                word.getWord(),
                word.getPos(),
                word.getCertLevel(),
                sense.getDefinition(),
                MINIMUM_EXAMPLES_PER_SENSE - existingCount
        );
    }

    private boolean hasStandardSense(UserVocabulary vocabulary) {
        return vocabulary != null
                && StringUtils.hasText(vocabulary.getWordId())
                && StringUtils.hasText(vocabulary.getSenseId())
                && !StringUtils.hasText(vocabulary.getSenseLocalizedId());
    }

    private String normalizeSentence(String sentence) {
        return sentence.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private record ExampleKey(String wordId, String senseId) {
    }
}
