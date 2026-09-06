package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.entity.ListenAndTypeExerciseChallenge;
import com.bachdauduc.vocab_app.entity.ListenExercise;
import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import com.bachdauduc.vocab_app.repository.ListenAndTypeExerciseChallengeRepository;
import com.bachdauduc.vocab_app.repository.ListenExerciseRepository;
import com.bachdauduc.vocab_app.service.abstraction.GetTranslation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListenAndTypeTranslationServiceTest {
    @Mock ListenAndTypeExerciseChallengeRepository repository;
    @Mock ListenExerciseRepository lessonRepository;
    @Mock GetTranslation translator;
    @InjectMocks ListenAndTypeTranslationService service;

    @Test
    void translatesFullDocumentOnceAndMapsRepeatedLinesByPosition() {
        var rows = List.of(challenge(0, "Old content", null),
                challenge(10, "Thank you.", "   "), challenge(20, "Hello.", null));
        storedRows(rows);
        document("Hello.\nThank you.\nHello.");
        persistUpdates(rows);
        when(translator.translateHtml(html("Hello.", "Thank you.", "Hello."), "vi"))
                .thenReturn(html("Xin chào.", "Cảm ơn.", "Chào lại."));

        assertThat(service.loadTranslatedChallenges("lesson")).extracting(ListenAndTypeExerciseChallenge::getTranslate)
                .containsExactly("Xin chào.", "Cảm ơn.", "Chào lại.");
        service.loadTranslatedChallenges("lesson");

        verify(translator, times(1)).translateHtml(anyString(), eq("vi"));
        verify(translator, never()).translate(anyList(), anyString());
        verify(repository).saveTranslationIfMissing("id-0", "Old content", "Xin chào.");
        verify(repository).saveTranslationIfMissing("id-20", "Hello.", "Chào lại.");
    }

    @Test
    void translatesFullDocumentStoredWithLiteralEscapedNewlines() {
        var rows = List.of(challenge(1, "Hello.", null), challenge(2, "Thank you.", null));
        storedRows(rows);
        document("Hello.\\nThank you.");
        persistUpdates(rows);
        when(translator.translateHtml(html("Hello.", "Thank you."), "vi"))
                .thenReturn(html("Xin chào.", "Cảm ơn."));

        assertThat(service.loadTranslatedChallenges("lesson"))
                .extracting(ListenAndTypeExerciseChallenge::getTranslate)
                .containsExactly("Xin chào.", "Cảm ơn.");
    }

    @Test
    void includesExistingTranslationsInContextButOnlyWritesMissingValues() {
        var rows = List.of(challenge(1, "Hello.", "Bản dịch đã duyệt"), challenge(2, "Thank you.", null));
        storedRows(rows);
        document("Hello.\r\nThank you.\r\n");
        persistUpdates(rows);
        when(translator.translateHtml(html("Hello.", "Thank you."), "vi"))
                .thenReturn(html("Xin chào.", "Cảm ơn."));

        assertThat(service.loadTranslatedChallenges("lesson")).extracting(ListenAndTypeExerciseChallenge::getTranslate)
                .containsExactly("Bản dịch đã duyệt", "Cảm ơn.");
        verify(repository, times(1)).saveTranslationIfMissing("id-2", "Thank you.", "Cảm ơn.");
    }

    @Test
    void fullyTranslatedOrEmptyLessonDoesNotLoadDocumentOrTranslate() {
        when(repository.findByListenExerciseIdOrderByPositionAsc("lesson"))
                .thenReturn(List.of(challenge(1, "Hello.", "Xin chào.")), List.of());
        assertThat(service.loadTranslatedChallenges("lesson")).hasSize(1);
        assertThat(service.loadTranslatedChallenges("lesson")).isEmpty();
        verifyNoInteractions(translator, lessonRepository);
        verify(repository, never()).saveTranslationIfMissing(any(), any(), any());
    }

    @Test
    void retriesWholeContextForBlankSegmentWithoutOverwritingSavedTranslation() {
        var rows = List.of(challenge(1, "Hello.", null), challenge(2, "Thank you.", null));
        storedRows(rows);
        document("Hello.\nThank you.");
        persistUpdates(rows);
        when(translator.translateHtml(html("Hello.", "Thank you."), "vi"))
                .thenReturn(html("Xin chào.", " "), html("Chào.", "Cảm ơn."));

        assertThat(service.loadTranslatedChallenges("lesson")).extracting(ListenAndTypeExerciseChallenge::getTranslate)
                .containsExactly("Xin chào.", null);
        assertThat(service.loadTranslatedChallenges("lesson")).extracting(ListenAndTypeExerciseChallenge::getTranslate)
                .containsExactly("Xin chào.", "Cảm ơn.");
        verify(repository, times(1)).saveTranslationIfMissing(eq("id-1"), any(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "Xin chào. [[SEG_1]] Cảm ơn.",
            "Xin chào. [[SEG_2]] Cảm ơn. [[SEG_1]]",
            "Xin chào. [[SEG_1]] Cảm ơn. [[SEG_1]]",
            "Xin chào. [[SEG_1]] Cảm ơn. [[SEG_2]] extra",
            "Xin chào. [[SEG_1]] Cảm ơn. [[SEG_2]] [[SEG_3]]",
            "Xin chào. [[SEG_1]] Cảm ơn. [[SEG_invalid]]"
    })
    void malformedMarkersNeverSaveAnyTranslation(String response) {
        storedRows(List.of(challenge(1, "Hello.", null), challenge(2, "Thank you.", null)));
        document("Hello.\nThank you.");
        when(translator.translateHtml(anyString(), eq("vi"))).thenReturn(response);

        assertThat(service.loadTranslatedChallenges("lesson")).hasSize(2);

        verify(repository, never()).saveTranslationIfMissing(any(), any(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "Hello.", "Hello.\n\nThank you.", "Hello. [[SEG_1]]\nThank you."})
    void invalidDocumentNeverCallsAzure(String source) {
        storedRows(List.of(challenge(1, "Hello.", null), challenge(2, "Thank you.", null)));
        document(source);

        assertThat(service.loadTranslatedChallenges("lesson")).hasSize(2);

        verifyNoInteractions(translator);
        verify(repository, never()).saveTranslationIfMissing(any(), any(), any());
    }

    @Test
    void missingLessonNeverCallsAzure() {
        storedRows(List.of(challenge(1, "Hello.", null)));
        assertThat(service.loadTranslatedChallenges("lesson")).hasSize(1);
        verifyNoInteractions(translator);
    }

    @Test
    void invalidPositionsNeverCallAzure() {
        var first = challenge(1, "Hello.", null);
        var second = challenge(1, "Thank you.", null);
        storedRows(List.of(first, second));
        document("Hello.\nThank you.");
        service.loadTranslatedChallenges("lesson");
        second.setPosition(null);
        service.loadTranslatedChallenges("lesson");
        verifyNoInteractions(translator);
    }

    @Test
    void escapesSourceHtmlAndDecodesTranslatedHtmlWithoutPersistingTags() {
        var rows = List.of(challenge(1, "<Don> & Sue.", null));
        storedRows(rows);
        document("<Don> & Sue.");
        persistUpdates(rows);
        when(translator.translateHtml(html("&lt;Don&gt; &amp; Sue."), "vi"))
                .thenReturn("<div><b>&lt;Don&gt; &amp; Sue.</b> <SPAN class='notranslate' translate='no'>[[SEG_1]]</SPAN></div>");

        assertThat(service.loadTranslatedChallenges("lesson").getFirst().getTranslate())
                .isEqualTo("<Don> & Sue.");
    }

    @Test
    void moreThanOneHundredChallengesStillUseOneContextualRequest() {
        var rows = IntStream.rangeClosed(1, 101).mapToObj(i -> challenge(i, "Sentence " + i, null)).toList();
        storedRows(rows);
        document(String.join("\n", rows.stream().map(ListenAndTypeExerciseChallenge::getContent).toList()));
        when(translator.translateHtml(anyString(), eq("vi"))).thenReturn(null);
        service.loadTranslatedChallenges("lesson");
        verify(translator, times(1)).translateHtml(anyString(), eq("vi"));
    }

    @Test
    void characterLimitIncludesEscapingAndMarkersAndNeverSplitsDocument() {
        var rows = List.of(challenge(1, "a".repeat(49_990), null));
        storedRows(rows);
        document(rows.getFirst().getContent());
        service.loadTranslatedChallenges("lesson");
        verifyNoInteractions(translator);
    }

    @Test
    void azureFailureReturnsPersistedChallengesAndCanRetry() {
        var rows = List.of(challenge(1, "Hello.", null));
        storedRows(rows);
        document("Hello.");
        persistUpdates(rows);
        when(translator.translateHtml(anyString(), eq("vi")))
                .thenThrow(new AppException(ErrorCode.TRANSLATION_FAILED)).thenReturn(html("Xin chào."));
        assertThat(service.loadTranslatedChallenges("lesson").getFirst().getTranslate()).isNull();
        assertThat(service.loadTranslatedChallenges("lesson").getFirst().getTranslate()).isEqualTo("Xin chào.");
    }

    @Test
    void concurrentRequestsForSameLessonReuseFirstRequestsTranslations() throws Exception {
        var rows = List.of(challenge(1, "Hello.", null));
        storedRows(rows);
        document("Hello.");
        persistUpdates(rows);
        CountDownLatch translating = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        when(translator.translateHtml(html("Hello."), "vi")).thenAnswer(invocation -> {
            translating.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return html("Xin chào.");
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.loadTranslatedChallenges("lesson"));
            try {
                assertThat(translating.await(5, TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> {
                    secondStarted.countDown();
                    return service.loadTranslatedChallenges("lesson");
                });
                assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
                release.countDown();
                assertThat(first.get(5, TimeUnit.SECONDS).getFirst().getTranslate()).isEqualTo("Xin chào.");
                assertThat(second.get(5, TimeUnit.SECONDS).getFirst().getTranslate()).isEqualTo("Xin chào.");
            } finally {
                release.countDown();
            }
        }
        verify(translator, times(1)).translateHtml(anyString(), eq("vi"));
    }

    @Test
    void translatesDocumentLineEvenWhenChallengeContentIsNull() {
        var rows = List.of(challenge(1, null, null));
        storedRows(rows);
        document("Hello.");
        persistUpdates(rows);
        when(translator.translateHtml(html("Hello."), "vi")).thenReturn(html("Xin chào."));

        assertThat(service.loadTranslatedChallenges("lesson").getFirst().getTranslate()).isEqualTo("Xin chào.");
        verify(repository).saveTranslationIfMissing("id-1", null, "Xin chào.");
    }

    private static String html(String... lines) {
        return IntStream.range(0, lines.length)
                .mapToObj(i -> lines[i] + " <span translate=\"no\">[[SEG_" + (i + 1) + "]]</span>")
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private void document(String source) {
        var lesson = new ListenExercise();
        lesson.setLessonId("lesson");
        lesson.setFullDocument(source);
        when(lessonRepository.findById("lesson")).thenReturn(Optional.of(lesson));
    }

    private void storedRows(List<ListenAndTypeExerciseChallenge> rows) {
        when(repository.findByListenExerciseIdOrderByPositionAsc("lesson")).thenReturn(rows);
    }

    private void persistUpdates(List<ListenAndTypeExerciseChallenge> rows) {
        when(repository.saveTranslationIfMissing(anyString(), nullable(String.class), anyString())).thenAnswer(invocation -> {
            rows.stream().filter(row -> row.getId().equals(invocation.getArgument(0)))
                    .findFirst().orElseThrow().setTranslate(invocation.getArgument(2));
            return 1;
        });
    }

    private ListenAndTypeExerciseChallenge challenge(int position, String content, String translation) {
        var challenge = new ListenAndTypeExerciseChallenge();
        challenge.setId("id-" + position);
        challenge.setPosition(position);
        challenge.setListenExerciseId("lesson");
        challenge.setContent(content);
        challenge.setSolution("Different answer " + position);
        challenge.setTranslate(translation);
        return challenge;
    }
}
