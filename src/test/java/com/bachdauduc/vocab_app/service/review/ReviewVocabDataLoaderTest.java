package com.bachdauduc.vocab_app.service.review;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewVocabDataLoaderTest {
    @Mock WordRepository wordRepository;
    @Mock WordSenseRepository wordSenseRepository;
    @Mock WordSenseLocalizationRepository wordSenseLocalizationRepository;
    @Mock WordSoundRepository wordSoundRepository;
    @Mock WordExampleRepository wordExampleRepository;
    @Mock WordExampleLocalizationRepository wordExampleLocalizationRepository;
    @Mock ReviewSnapshotCache snapshotCache;

    ReviewVocabDataLoader loader;

    @BeforeEach
    void setUp() {
        loader = new ReviewVocabDataLoader(
                wordRepository,
                wordSenseRepository,
                wordSenseLocalizationRepository,
                wordSoundRepository,
                wordExampleRepository,
                wordExampleLocalizationRepository,
                snapshotCache
        );
    }

    @Test
    void bulkLoadsOneStandardSenseAndCachesTheSnapshot() {
        UserVocabulary vocabulary = vocabulary();
        Word word = word();
        WordSense sense = sense();
        WordSenseLocalization localization = localization();
        WordSound sound = sound();
        WordExample example = example();
        WordExampleLocalization exampleLocalization = exampleLocalization();
        when(snapshotCache.lookup(any())).thenReturn(
                new ReviewSnapshotLookup(Map.of(), Map.of("word-1", 0L)));
        when(wordRepository.findAllById(any())).thenReturn(List.of(word));
        when(wordSenseRepository.findAllById(any())).thenReturn(List.of(sense));
        when(wordSenseLocalizationRepository.findBySenseIdInAndLangCode(any(), eq("vi")))
                .thenReturn(List.of(localization));
        when(wordSoundRepository.findByWordIdIn(any())).thenReturn(List.of(sound));
        when(wordExampleRepository.findBySenseIdIn(any())).thenReturn(List.of(example));
        when(wordExampleLocalizationRepository.findByExampleIdInAndLangCode(any(), eq("vi")))
                .thenReturn(List.of(exampleLocalization));

        Map<String, ReviewVocabSnapshot> result = loader.load(List.of(vocabulary), "vi");

        assertThat(result).containsKey("uv-1");
        assertThat(result.get("uv-1").meaning()).isEqualTo("bờ sông");
        assertThat(result.get("uv-1").playableSoundUrl()).contains("audio.mp3");
        assertThat(result.get("uv-1").examples()).containsExactly(
                new ReviewExample("example-1", "The bank was flooded.", "Bờ sông bị ngập.")
        );
        verify(wordRepository).findAllById(any());
        verify(wordSenseRepository).findAllById(any());
        verify(wordSoundRepository).findByWordIdIn(any());
        verify(snapshotCache).putAll(any(), any(), any());
    }

    @Test
    void loadsLocalizedOnlySenseWithoutLookingUpANullStandardSenseId() {
        UserVocabulary vocabulary = new UserVocabulary();
        vocabulary.setId("uv-1");
        vocabulary.setWordId("word-1");
        vocabulary.setSenseLocalizedId("localization-1");

        WordSenseLocalization localizedSense = new WordSenseLocalization();
        localizedSense.setId("localization-1");
        localizedSense.setWordId("word-1");
        localizedSense.setSource("MOCHI");
        localizedSense.setLangCode("vi");
        localizedSense.setShortMeaning("MOCHI meaning");

        when(snapshotCache.lookup(any())).thenReturn(
                new ReviewSnapshotLookup(Map.of(), Map.of("word-1", 0L)));
        when(wordRepository.findAllById(any())).thenReturn(List.of(word()));
        when(wordSenseRepository.findAllById(any())).thenReturn(List.of());
        when(wordSenseLocalizationRepository.findAllById(any())).thenReturn(List.of(localizedSense));
        when(wordSoundRepository.findByWordIdIn(any())).thenReturn(List.of());
        when(wordExampleRepository.findBySenseIdIn(any())).thenReturn(List.of());

        Map<String, ReviewVocabSnapshot> result = loader.load(List.of(vocabulary), "vi");

        assertThat(result).containsKey("uv-1");
        assertThat(result.get("uv-1").meaning()).isEqualTo("MOCHI meaning");
    }

    @Test
    void prefersLocalizedSenseWhenBothSenseIdentifiersExist() {
        UserVocabulary vocabulary = new UserVocabulary();
        vocabulary.setId("uv-1");
        vocabulary.setWordId("word-1");
        vocabulary.setSenseId("sense-1");
        vocabulary.setSenseLocalizedId("localization-1");

        WordSenseLocalization localizedSense = new WordSenseLocalization();
        localizedSense.setId("localization-1");
        localizedSense.setWordId("word-1");
        localizedSense.setSenseId("sense-1");
        localizedSense.setSource("CUSTOM");
        localizedSense.setLangCode("vi");
        localizedSense.setShortMeaning("selected localized meaning");

        WordSenseLocalization standardTranslation = new WordSenseLocalization();
        standardTranslation.setId("translation-1");
        standardTranslation.setWordId("word-1");
        standardTranslation.setSenseId("sense-1");
        standardTranslation.setSource("CUSTOM");
        standardTranslation.setLangCode("vi");
        standardTranslation.setShortMeaning("standard translation");

        when(snapshotCache.lookup(any())).thenReturn(
                new ReviewSnapshotLookup(Map.of(), Map.of("word-1", 0L)));
        when(wordRepository.findAllById(any())).thenReturn(List.of(word()));
        when(wordSenseRepository.findAllById(any())).thenReturn(List.of(sense()));
        when(wordSenseLocalizationRepository.findAllById(any())).thenReturn(List.of(localizedSense));
        when(wordSenseLocalizationRepository.findBySenseIdInAndLangCode(any(), eq("vi")))
                .thenReturn(List.of(standardTranslation));
        when(wordSoundRepository.findByWordIdIn(any())).thenReturn(List.of());
        when(wordExampleRepository.findBySenseIdIn(any())).thenReturn(List.of());

        Map<String, ReviewVocabSnapshot> result = loader.load(List.of(vocabulary), "vi");

        assertThat(result.get("uv-1").meaning()).isEqualTo("selected localized meaning");
    }

    private UserVocabulary vocabulary() {
        UserVocabulary value = new UserVocabulary();
        value.setId("uv-1");
        value.setWordId("word-1");
        value.setSenseId("sense-1");
        value.setLevel(1);
        return value;
    }

    private Word word() {
        Word value = new Word();
        value.setId("word-1");
        value.setWord("bank");
        value.setPos("noun");
        return value;
    }

    private WordSense sense() {
        WordSense value = new WordSense();
        value.setId("sense-1");
        value.setWordId("word-1");
        value.setDefinition("land beside a river");
        return value;
    }

    private WordSenseLocalization localization() {
        WordSenseLocalization value = new WordSenseLocalization();
        value.setId("localization-1");
        value.setSenseId("sense-1");
        value.setWordId("word-1");
        value.setLangCode("vi");
        value.setShortMeaning("bờ sông");
        return value;
    }

    private WordSound sound() {
        WordSound value = new WordSound();
        value.setId("sound-1");
        value.setWordId("word-1");
        value.setSoundSource("MOCHI");
        value.setMp3Url("audio.mp3");
        value.setTags("[]");
        return value;
    }

    private WordExample example() {
        WordExample value = new WordExample();
        value.setId("example-1");
        value.setWordId("word-1");
        value.setSenseId("sense-1");
        value.setText("The bank was flooded.");
        return value;
    }

    private WordExampleLocalization exampleLocalization() {
        WordExampleLocalization value = new WordExampleLocalization();
        value.setId("example-localization-1");
        value.setExampleId("example-1");
        value.setWordId("word-1");
        value.setSenseId("sense-1");
        value.setLangCode("vi");
        value.setTranslatedText("Bờ sông bị ngập.");
        return value;
    }
}
