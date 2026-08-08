package com.bachdauduc.vocab_app.service.review;

import com.bachdauduc.vocab_app.constant.ExerciseType;
import com.bachdauduc.vocab_app.dto.response.exercise.VocabReviewQuizResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordExampleResponse;
import com.bachdauduc.vocab_app.entity.UserVocabulary;
import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ReviewQuizFactory {
    private final RandomGenerator random;

    public ReviewQuizFactory() {
        this(new java.util.Random());
    }

    ReviewQuizFactory(RandomGenerator random) {
        this.random = random;
    }

    public Set<ExerciseType> eligibleTypes(
            UserVocabulary target,
            ReviewVocabSnapshot snapshot,
            ReviewRequestContext context
    ) {
        EnumSet<ExerciseType> eligible = EnumSet.noneOf(ExerciseType.class);
        boolean hasWord = StringUtils.hasText(snapshot.word());
        boolean hasMeaning = StringUtils.hasText(snapshot.meaning());
        boolean hasExample = !snapshot.examples().isEmpty();
        boolean hasSound = snapshot.playableSoundUrl().isPresent();
        boolean canMask = normalizedLetterCount(snapshot.word()) > 2;

        if (hasWord && hasMeaning) {
            eligible.add(ExerciseType.VOCAB_WORD_TO_MEANING);
        }
        if (hasWord && canMask) {
            eligible.add(ExerciseType.VOCAB_FILL_MISSING_WORD_PART);
        }
        if (hasWord && hasSound) {
            eligible.add(ExerciseType.VOCAB_LISTEN_AND_TYPE_WORD);
        }
        if (hasWord && hasExample) {
            eligible.add(ExerciseType.VOCAB_CHOOSE_WORD_IN_SENTENCE_BLANK);
        }
        if (hasWord && hasExample && canMask) {
            eligible.add(ExerciseType.VOCAB_FILL_WORD_IN_SENTENCE_BLANK);
        }
        if (hasMeaning && hasSound && distinctWithout(context.soundDistractors(),
                snapshot.playableSoundUrl().orElse(null)).size() >= 3) {
            eligible.add(ExerciseType.VOCAB_MEANING_TO_SOUND);
        }
        if (hasExample && hasMeaning
                && distinctWithout(context.meaningDistractors(), snapshot.meaning()).size() >= 3) {
            eligible.add(ExerciseType.VOCAB_SENTENCE_TO_MEANING);
        }
        if (hasExample && hasSound && distinctWithout(context.soundDistractors(),
                snapshot.playableSoundUrl().orElse(null)).size() >= 3) {
            eligible.add(ExerciseType.VOCAB_SENTENCE_BLANK_TO_SOUND);
        }
        return Collections.unmodifiableSet(eligible);
    }

    public VocabReviewQuizResponse create(
            UserVocabulary target,
            ReviewVocabSnapshot snapshot,
            ReviewRequestContext context,
            ExerciseType type
    ) {
        if (!eligibleTypes(target, snapshot, context).contains(type)) {
            throw new AppException(ErrorCode.INVALID_EXERCISE_TYPE);
        }

        VocabReviewQuizResponse.VocabReviewQuizResponseBuilder response = baseResponse(target, snapshot, type);
        return switch (type) {
            case VOCAB_WORD_TO_MEANING -> response
                    .correctAnswer(snapshot.meaning())
                    .listAnswers(options(context.meaningDistractors(), snapshot.meaning(), 4))
                    .build();
            case VOCAB_FILL_MISSING_WORD_PART -> {
                MaskedWord masked = maskedWord(snapshot.word(), target.getLevel());
                yield response.correctAnswer(snapshot.word())
                        .metadata(masked.characters())
                        .maskedWord(masked.value())
                        .build();
            }
            case VOCAB_LISTEN_AND_TYPE_WORD -> response
                    .correctAnswer(snapshot.word())
                    .audioUrl(snapshot.playableSoundUrl()
                            .orElseThrow(() -> new AppException(ErrorCode.WORD_SOUND_NOT_FOUND)))
                    .build();
            case VOCAB_CHOOSE_WORD_IN_SENTENCE_BLANK -> {
                SentenceTransform sentence = transformSentence(snapshot, SentenceMode.BLANK, null);
                yield response.correctAnswer(snapshot.word())
                        .missIndex(sentence.index())
                        .sentence(sentence.sentence())
                        .trans(sentence.translation())
                        .listAnswers(options(context.wordDistractors(), snapshot.word(), 4))
                        .build();
            }
            case VOCAB_FILL_WORD_IN_SENTENCE_BLANK -> {
                MaskedWord masked = maskedWord(snapshot.word(), target.getLevel());
                SentenceTransform sentence = transformSentence(snapshot, SentenceMode.REPLACE, masked.value());
                yield response.correctAnswer(snapshot.word())
                        .metadata(masked.characters())
                        .maskedWord(masked.value())
                        .missIndex(sentence.index())
                        .sentence(sentence.sentence())
                        .trans(sentence.translation())
                        .build();
            }
            case VOCAB_MEANING_TO_SOUND -> {
                IndexedOptions indexed = indexedOptions(
                        context.soundDistractors(),
                        snapshot.playableSoundUrl().orElseThrow(),
                        ErrorCode.WORD_SOUND_NOT_FOUND
                );
                ReviewExample example = preferredExample(snapshot);
                yield response.correctAnswer(indexed.correctAnswer())
                        .metadata(indexed.values())
                        .sentence(example == null ? null : example.sentence())
                        .trans(example == null ? null : example.translation())
                        .build();
            }
            case VOCAB_SENTENCE_TO_MEANING -> {
                SentenceTransform sentence = transformSentence(snapshot, SentenceMode.UNDERLINE, null);
                IndexedOptions indexed = indexedOptions(
                        context.meaningDistractors(), snapshot.meaning(), ErrorCode.INVALID_EXERCISE_TYPE);
                yield response.correctAnswer(indexed.correctAnswer())
                        .metadata(indexed.values())
                        .missIndex(sentence.index())
                        .sentence(sentence.sentence())
                        .trans(sentence.translation())
                        .build();
            }
            case VOCAB_SENTENCE_BLANK_TO_SOUND -> {
                SentenceTransform sentence = transformSentence(snapshot, SentenceMode.BLANK, null);
                IndexedOptions indexed = indexedOptions(
                        context.soundDistractors(),
                        snapshot.playableSoundUrl().orElseThrow(),
                        ErrorCode.WORD_SOUND_NOT_FOUND
                );
                yield response.correctAnswer(indexed.correctAnswer())
                        .metadata(indexed.values())
                        .missIndex(sentence.index())
                        .sentence(sentence.sentence())
                        .trans(sentence.translation())
                        .build();
            }
            default -> throw new AppException(ErrorCode.INVALID_EXERCISE_TYPE);
        };
    }

    private VocabReviewQuizResponse.VocabReviewQuizResponseBuilder baseResponse(
            UserVocabulary target,
            ReviewVocabSnapshot snapshot,
            ExerciseType type
    ) {
        return VocabReviewQuizResponse.builder()
                .wordId(snapshot.wordId())
                .userVocabId(target.getId())
                .word(snapshot.word())
                .pos(snapshot.pos())
                .sound(snapshot.preferredSound().orElse(null))
                .example(toExample(target, snapshot, preferredExample(snapshot)))
                .sense(snapshot.wordSense())
                .wordSense(snapshot.wordSense())
                .exerciseType(type);
    }

    private WordExampleResponse toExample(
            UserVocabulary target,
            ReviewVocabSnapshot snapshot,
            ReviewExample example
    ) {
        if (example == null) {
            return null;
        }
        return WordExampleResponse.builder()
                .wordExampleId(example.id())
                .senseId(target.getSenseId())
                .wordSenseLocalizationId(target.getSenseLocalizedId())
                .wordId(snapshot.wordId())
                .word(snapshot.word())
                .pos(snapshot.pos())
                .sentence(example.sentence())
                .trans(example.translation())
                .build();
    }

    private List<String> options(List<String> values, String correct, int total) {
        List<String> answers = new ArrayList<>(distinctWithout(values, correct));
        shuffle(answers);
        if (answers.size() > total - 1) {
            answers = new ArrayList<>(answers.subList(0, total - 1));
        }
        answers.add(correct);
        shuffle(answers);
        return List.copyOf(answers);
    }

    private IndexedOptions indexedOptions(List<String> values, String correct, ErrorCode errorCode) {
        if (!StringUtils.hasText(correct)) {
            throw new AppException(errorCode);
        }
        List<String> options = options(values, correct, 4);
        if (options.size() < 4) {
            throw new AppException(errorCode);
        }
        Map<Integer, String> metadata = new LinkedHashMap<>();
        String correctAnswer = null;
        for (int index = 0; index < options.size(); index++) {
            int key = index + 1;
            metadata.put(key, options.get(index));
            if (correct.equals(options.get(index))) {
                correctAnswer = String.valueOf(key);
            }
        }
        return new IndexedOptions(metadata, correctAnswer);
    }

    private List<String> distinctWithout(List<String> values, String excluded) {
        return values.stream()
                .filter(StringUtils::hasText)
                .filter(value -> !value.equals(excluded))
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new
                ));
    }

    private MaskedWord maskedWord(String word, Integer level) {
        List<Integer> candidates = new ArrayList<>();
        for (int index = 1; index < word.length(); index++) {
            if (!Character.isWhitespace(word.charAt(index))) {
                candidates.add(index);
            }
        }
        shuffle(candidates);
        int count = resolveMissingCount(candidates.size() + 1, level);
        Map<Integer, String> characters = new LinkedHashMap<>();
        candidates.stream()
                .limit(Math.min(count, candidates.size()))
                .sorted(Comparator.naturalOrder())
                .forEach(index -> characters.put(index, String.valueOf(word.charAt(index))));
        StringBuilder value = new StringBuilder(word);
        characters.keySet().forEach(index -> value.setCharAt(index, '_'));
        return new MaskedWord(value.toString(), characters);
    }

    private int resolveMissingCount(int length, Integer level) {
        boolean highLevel = level != null && level >= 4;
        if (length == 3) {
            return randomBetween(1, 2);
        }
        if (length == 4) {
            return highLevel ? 3 : randomBetween(1, 2);
        }
        return highLevel ? randomBetween(3, Math.max(3, length - 1)) : randomBetween(1, 2);
    }

    private int randomBetween(int minimum, int maximum) {
        return random.nextInt(minimum, maximum + 1);
    }

    private SentenceTransform transformSentence(
            ReviewVocabSnapshot snapshot,
            SentenceMode mode,
            String replacement
    ) {
        ReviewExample example = randomExample(snapshot);
        if (!StringUtils.hasText(example.sentence())) {
            throw new AppException(ErrorCode.WORD_EXAMPLE_NOT_FOUND);
        }
        Pattern pattern = Pattern.compile(
                Pattern.quote(snapshot.word()),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
        );
        Matcher matcher = pattern.matcher(example.sentence());
        if (!matcher.find()) {
            return new SentenceTransform(example.sentence(), example.translation(), -1);
        }
        String value = switch (mode) {
            case BLANK -> "_".repeat(matcher.end() - matcher.start());
            case REPLACE -> replacement;
            case UNDERLINE -> "<u>" + example.sentence().substring(matcher.start(), matcher.end()) + "</u>";
        };
        String sentence = example.sentence().substring(0, matcher.start())
                + value
                + example.sentence().substring(matcher.end());
        return new SentenceTransform(sentence, example.translation(), matcher.start());
    }

    private ReviewExample randomExample(ReviewVocabSnapshot snapshot) {
        if (snapshot.examples().isEmpty()) {
            throw new AppException(ErrorCode.WORD_EXAMPLE_NOT_FOUND);
        }
        return snapshot.examples().get(random.nextInt(snapshot.examples().size()));
    }

    private ReviewExample preferredExample(ReviewVocabSnapshot snapshot) {
        return snapshot.examples().stream()
                .filter(example -> StringUtils.hasText(example.translation()))
                .findFirst()
                .or(() -> snapshot.examples().stream().findFirst())
                .orElse(null);
    }

    private int normalizedLetterCount(String word) {
        if (!StringUtils.hasText(word)) {
            return 0;
        }
        return Normalizer.normalize(word, Normalizer.Form.NFKC)
                .replaceAll("\\s+", "")
                .length();
    }

    private <T> void shuffle(List<T> values) {
        for (int index = values.size() - 1; index > 0; index--) {
            int other = random.nextInt(index + 1);
            Collections.swap(values, index, other);
        }
    }

    private enum SentenceMode {
        BLANK,
        REPLACE,
        UNDERLINE
    }

    private record MaskedWord(String value, Map<Integer, String> characters) {
    }

    private record IndexedOptions(Map<Integer, String> values, String correctAnswer) {
    }

    private record SentenceTransform(String sentence, String translation, int index) {
    }
}
