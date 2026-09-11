package com.bachdauduc.vocab_app.service.review;

import com.bachdauduc.vocab_app.constant.ExerciseType;
import com.bachdauduc.vocab_app.dto.response.exercise.VocabReviewQuizResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordSoundResponse;
import com.bachdauduc.vocab_app.entity.UserVocabulary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewSentenceRegressionTest {
    private final ReviewQuizFactory factory = new ReviewQuizFactory(new Random(7));

    @ParameterizedTest
    @CsvSource({
            "Please wrap that up.,Please <u>wrap</u> that <u>up</u>.",
            "Wrap it and hold it up; then wrap it up.,Wrap it and hold it up; then <u>wrap</u> it <u>up</u>.",
            "Say 'wrap up' clearly.,Say '<u>wrap up</u>' clearly."
    })
    void handlesDemonstrativesLaterMatchesAndQuotedPhrases(String sentence, String expected) {
        assertThat(quiz("wrap up", "verb", sentence, ExerciseType.VOCAB_SENTENCE_TO_MEANING).getSentence())
                .isEqualTo(expected);
    }

    @Test
    void usesDictionaryFormsForIrregularSeparatedPhrases() {
        var base = snapshot("take off", "verb", List.of(new ReviewExample("selected", "She took her coat off.", "translation")));
        var snapshot = new ReviewVocabSnapshot(base.schemaVersion(), base.wordId(), base.senseKey(), base.langCode(),
                base.word(), base.pos(), base.meaning(), base.wordSense(), base.sounds(), base.examples(),
                base.generatedAt(), List.of("took off", "taken off"));
        var quiz = factory.create(target(), snapshot, context(snapshot), ExerciseType.VOCAB_SENTENCE_BLANK_TO_SOUND);
        assertThat(quiz.getSentence()).isEqualTo("She ____ her coat ___.");
        assertThat(quiz.getTargetSpans()).extracting(span -> span.text()).containsExactly("took", "off");
    }

    @Test
    void hintedSeparatedPhraseKeepsObjectAndCharacterIndexes() {
        var quiz = quiz("wrap up", "verb", "She wrapped it up.", ExerciseType.VOCAB_FILL_WORD_IN_SENTENCE_BLANK);
        assertThat(quiz.getCorrectAnswer()).isEqualTo("wrapped up");
        var parts = quiz.getMaskedWord().split(" ");
        assertThat(quiz.getSentence()).isEqualTo("She " + parts[0] + " it " + parts[1] + ".");
        StringBuilder restored = new StringBuilder(quiz.getMaskedWord());
        quiz.getMetadata().forEach((index, value) -> restored.setCharAt(index, value.charAt(0)));
        assertThat(restored.toString()).isEqualTo("wrapped up");
        assertThat(quiz.getTargetSpans()).allSatisfy(span ->
                assertThat(quiz.getExample().getSentence().substring(span.start(), span.end())).isEqualTo(span.text()));
    }

    @ParameterizedTest
    @CsvSource({
            "art,noun,An artist paints.",
            "run,verb,The runway is closed.",
            "wrap up,verb,Wrap it. Up the road is home.",
            "wrap up,verb,Wrap it and walk up the road.",
            "look at,verb,Look around at the view."
            ,"speak up,verb,She speaks French up north."
            ,"can,verb,We can't go."
    })
    void rejectsPartialWordsAndUnsafeSeparatedMatches(String word, String pos, String sentence) {
        assertThat(ReviewSentenceMatcher.find(sentence, word, pos, List.of())).isEmpty();
    }

    @Test
    void preservesWhitespaceCaseAndUtf16Offsets() {
        var quiz = quiz("consist of", "verb", "😀 It CONSISTS\u00a0OF water.", ExerciseType.VOCAB_SENTENCE_TO_MEANING);
        assertThat(quiz.getMaskedWord()).isEqualTo("CONSISTS\u00a0OF");
        assertThat(quiz.getMissIndex()).isEqualTo(6);
        assertThat(quiz.getTargetSpans().getFirst().end()).isEqualTo(17);
    }

    @ParameterizedTest
    @CsvSource({
            "consist of,verb,It consists of water.,consists of,3",
            "consist of,verb,It consisted of water.,consisted of,3",
            "study,verb,She studies daily.,studies,4",
            "make,verb,She is making tea.,making,7",
            "run,verb,He is running daily.,running,6",
            "planet,noun,PLANETS orbit stars.,PLANETS,0",
            "art,noun,An artist makes art.,art,16"
    })
    void highlightsWholeSurfaceForm(String word, String pos, String sentence, String surface, int start) {
        var quiz = quiz(word, pos, sentence, ExerciseType.VOCAB_SENTENCE_TO_MEANING);
        assertThat(quiz.getMaskedWord()).isEqualTo(surface);
        assertThat(quiz.getMissIndex()).isEqualTo(start);
        assertThat(quiz.getSentence()).isEqualTo(sentence.substring(0, start) + "<u>" + surface + "</u>"
                + sentence.substring(start + surface.length()));
    }

    @Test
    void blanksTheWholeInflectedPhraseForBothChoiceTypes() {
        for (var type : List.of(ExerciseType.VOCAB_CHOOSE_WORD_IN_SENTENCE_BLANK,
                ExerciseType.VOCAB_SENTENCE_BLANK_TO_SOUND)) {
            var quiz = quiz("consist of", "verb", "It consists of water.", type);
            assertThat(quiz.getSentence()).isEqualTo("It ___________ water.");
            assertThat(quiz.getMaskedWord()).isEqualTo("___________");
            assertThat(quiz.getMissIndex()).isEqualTo(3);
            if (type == ExerciseType.VOCAB_CHOOSE_WORD_IN_SENTENCE_BLANK) {
                assertThat(quiz.getCorrectAnswer()).isEqualTo("consists of");
                assertThat(quiz.getListAnswers()).contains("consists of").doesNotContain("consist of");
            }
        }
    }

    @Test
    void hintedAnswerReconstructsTheOriginalSurfaceForm() {
        var quiz = quiz("consist of", "verb", "It consists of water.", ExerciseType.VOCAB_FILL_WORD_IN_SENTENCE_BLANK);
        assertThat(quiz.getCorrectAnswer()).isEqualTo("consists of");
        assertThat(quiz.getWord()).isEqualTo("consist of");
        StringBuilder restored = new StringBuilder(quiz.getMaskedWord());
        quiz.getMetadata().forEach((index, value) -> restored.setCharAt(index, value.charAt(0)));
        assertThat(restored.toString()).isEqualTo("consists of");
        assertThat(quiz.getSentence()).isEqualTo("It " + quiz.getMaskedWord() + " water.");
    }

    @Test
    void preservesTheObjectWhenBlankingSeparatedPhrasalVerbs() {
        for (var type : List.of(ExerciseType.VOCAB_CHOOSE_WORD_IN_SENTENCE_BLANK,
                ExerciseType.VOCAB_SENTENCE_BLANK_TO_SOUND)) {
            var quiz = quiz("wrap up", "verb", "Please wrap it up.", type);
            assertThat(quiz.getSentence()).isEqualTo("Please ____ it __.");
            assertThat(quiz.getMaskedWord()).isEqualTo("____ __");
            assertThat(quiz.getMissIndex()).isEqualTo(7);
        }
    }

    @Test
    void highlightsSeparatedAndInflectedPhrasalVerbsWithoutHighlightingTheObject() {
        var quiz = quiz("wrap up", "verb", "She wrapped the meeting up.", ExerciseType.VOCAB_SENTENCE_TO_MEANING);
        assertThat(quiz.getSentence()).isEqualTo("She <u>wrapped</u> the meeting <u>up</u>.");
        assertThat(quiz.getMaskedWord()).isEqualTo("wrapped up");
    }

    @Test
    void excludesAllSentenceTypesWhenOnlySubstringOrUnrelatedExamplesExist() {
        var snapshot = snapshot("art", "noun", List.of(new ReviewExample("bad", "An artist paints.", null)));
        assertThat(factory.eligibleTypes(target(), snapshot, context(snapshot))).doesNotContainAnyElementsOf(sentenceTypes());
    }

    @Test
    void selectsOnlyUsableExamplesAndAlignsTheResponseExample() {
        var snapshot = snapshot("consist of", "verb", List.of(
                new ReviewExample("bad", "Unrelated sentence.", "bad translation"),
                new ReviewExample("good", "It consists of water.", "good translation")));
        for (var type : sentenceTypes()) {
            var quiz = factory.create(target(), snapshot, context(snapshot), type);
            assertThat(quiz.getExample().getWordExampleId()).isEqualTo("good");
            assertThat(quiz.getTrans()).isEqualTo("good translation");
            assertThat(quiz.getMissIndex()).isEqualTo(3);
        }
    }

    private List<ExerciseType> sentenceTypes() {
        return List.of(ExerciseType.VOCAB_SENTENCE_TO_MEANING, ExerciseType.VOCAB_CHOOSE_WORD_IN_SENTENCE_BLANK,
                ExerciseType.VOCAB_FILL_WORD_IN_SENTENCE_BLANK, ExerciseType.VOCAB_SENTENCE_BLANK_TO_SOUND);
    }

    private VocabReviewQuizResponse quiz(String word, String pos, String sentence, ExerciseType type) {
        var snapshot = snapshot(word, pos, List.of(new ReviewExample("selected", sentence, "translation")));
        return factory.create(target(), snapshot, context(snapshot), type);
    }

    private UserVocabulary target() {
        var target = new UserVocabulary();
        target.setId("uv");
        target.setWordId("word");
        target.setSenseId("sense");
        target.setLevel(3);
        return target;
    }

    private ReviewVocabSnapshot snapshot(String word, String pos, List<ReviewExample> examples) {
        return new ReviewVocabSnapshot(ReviewVocabSnapshot.CURRENT_SCHEMA_VERSION, word, "sense", "vi", word,
                pos, "meaning of " + word, null,
                List.of(WordSoundResponse.builder().mp3Url("https://audio/" + word).build()), examples, Instant.now());
    }

    private ReviewRequestContext context(ReviewVocabSnapshot snapshot) {
        List<UserVocabulary> vocabularies = new java.util.ArrayList<>(List.of(target()));
        for (String id : List.of("a", "b", "c")) {
            var other = new UserVocabulary();
            other.setId(id);
            vocabularies.add(other);
        }
        return ReviewRequestContext.create(vocabularies, Map.of("uv", snapshot,
                "a", snapshot("forest", "noun", List.of()), "b", snapshot("river", "noun", List.of()),
                "c", snapshot("ocean", "noun", List.of())));
    }
}
