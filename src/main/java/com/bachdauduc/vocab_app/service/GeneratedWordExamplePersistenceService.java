package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.entity.WordExample;
import com.bachdauduc.vocab_app.entity.WordExampleLocalization;
import com.bachdauduc.vocab_app.repository.WordExampleLocalizationRepository;
import com.bachdauduc.vocab_app.repository.WordExampleRepository;
import com.bachdauduc.vocab_app.service.model.GeneratedWordExample;
import com.bachdauduc.vocab_app.service.review.ReviewVocabCacheRevisionService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GeneratedWordExamplePersistenceService {
    private static final int MINIMUM_EXAMPLES_PER_SENSE = 4;
    private static final String EXAMPLE_TYPE = "AI_GENERATED";
    private static final String SOURCE_REF = "GROQ:openai/gpt-oss-120b";

    WordExampleRepository wordExampleRepository;
    WordExampleLocalizationRepository wordExampleLocalizationRepository;
    ReviewVocabCacheRevisionService reviewVocabCacheRevisionService;

    @Transactional
    public void persist(List<GeneratedWordExample> generatedExamples) {
        if (generatedExamples == null || generatedExamples.isEmpty()) {
            return;
        }

        Map<ExampleKey, List<GeneratedWordExample>> generatedBySense = generatedExamples.stream()
                .filter(this::isUsable)
                .collect(Collectors.groupingBy(
                        generated -> new ExampleKey(generated.wordId(), generated.senseId()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        Set<String> changedWordIds = new LinkedHashSet<>();
        for (Map.Entry<ExampleKey, List<GeneratedWordExample>> entry : generatedBySense.entrySet()) {
            ExampleKey key = entry.getKey();
            Set<String> existingSentences = wordExampleRepository
                    .findByWordIdAndSenseId(key.wordId(), key.senseId())
                    .stream()
                    .map(WordExample::getText)
                    .filter(StringUtils::hasText)
                    .map(this::normalizeSentence)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            for (GeneratedWordExample generated : entry.getValue()) {
                if (existingSentences.size() >= MINIMUM_EXAMPLES_PER_SENSE) {
                    break;
                }
                if (!existingSentences.add(normalizeSentence(generated.example()))) {
                    continue;
                }
                savePair(generated);
                changedWordIds.add(generated.wordId());
            }
        }
        reviewVocabCacheRevisionService.invalidateAfterCommit(changedWordIds);
    }

    private void savePair(GeneratedWordExample generated) {
        String exampleId = UUID.randomUUID().toString();

        WordExample example = new WordExample();
        example.setId(exampleId);
        example.setWordId(generated.wordId());
        example.setSenseId(generated.senseId());
        example.setText(generated.example().trim());
        example.setExampleType(EXAMPLE_TYPE);
        example.setSourceRef(SOURCE_REF);
        wordExampleRepository.save(example);

        WordExampleLocalization localization = new WordExampleLocalization();
        localization.setId(UUID.randomUUID().toString());
        localization.setExampleId(exampleId);
        localization.setWordId(generated.wordId());
        localization.setSenseId(generated.senseId());
        localization.setLangCode("vi");
        localization.setTranslatedText(generated.translatedExample().trim());
        localization.setReviewStatus(1);
        wordExampleLocalizationRepository.save(localization);
    }

    private boolean isUsable(GeneratedWordExample generated) {
        return generated != null
                && StringUtils.hasText(generated.wordId())
                && StringUtils.hasText(generated.senseId())
                && StringUtils.hasText(generated.example())
                && StringUtils.hasText(generated.translatedExample());
    }

    private String normalizeSentence(String sentence) {
        return sentence.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private record ExampleKey(String wordId, String senseId) {
    }
}
