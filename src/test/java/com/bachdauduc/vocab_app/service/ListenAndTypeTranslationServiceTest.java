package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.entity.ListenAndTypeExerciseChallenge;
import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import com.bachdauduc.vocab_app.repository.ListenAndTypeExerciseChallengeRepository;
import com.bachdauduc.vocab_app.service.abstraction.GetTranslation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
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
    @Mock GetTranslation translator;
    @InjectMocks ListenAndTypeTranslationService service;

    @Test
    void translatesEntireNewLessonOnceAndReusesSavedTranslationsOnReopen() {
        var first = challenge("1", "Hello.", null);
        var second = challenge("2", "Thank you.", "   ");
        var duplicate = challenge("3", "Hello.", null);
        List<ListenAndTypeExerciseChallenge> rows = List.of(first, second, duplicate);
        storedRows(rows);
        persistUpdates(rows);
        when(translator.translate(List.of("Hello.", "Thank you."), "vi"))
                .thenReturn(Map.of("Hello.", "Xin chào.", "Thank you.", "Cảm ơn."));

        assertThat(service.loadTranslatedChallenges("lesson")).extracting(ListenAndTypeExerciseChallenge::getTranslate)
                .containsExactly("Xin chào.", "Cảm ơn.", "Xin chào.");
        service.loadTranslatedChallenges("lesson");

        verify(translator, times(1)).translate(List.of("Hello.", "Thank you."), "vi");
        verify(repository, times(3)).saveTranslationIfMissing(anyString(), anyString(), anyString());
    }

    @Test
    void translatesOnlyMissingContentAndPreservesExistingValues() {
        var existing = challenge("1", "Hello.", "Bản dịch đã duyệt");
        var missing = challenge("2", "Thank you.", null);
        var noContent = challenge("3", "   ", null);
        List<ListenAndTypeExerciseChallenge> rows = List.of(existing, missing, noContent);
        storedRows(rows);
        persistUpdates(rows);
        when(translator.translate(List.of("Thank you."), "vi"))
                .thenReturn(Map.of("Thank you.", "Cảm ơn."));

        assertThat(service.loadTranslatedChallenges("lesson")).extracting(ListenAndTypeExerciseChallenge::getTranslate)
                .containsExactly("Bản dịch đã duyệt", "Cảm ơn.", null);
        verify(repository).saveTranslationIfMissing("2", "Thank you.", "Cảm ơn.");
        verify(repository, never()).saveTranslationIfMissing(eq("1"), any(), any());
    }

    @Test
    void fullyTranslatedOrEmptyLessonDoesNotCallTranslatorOrWriteDatabase() {
        when(repository.findByListenExerciseIdOrderByPositionAsc("lesson"))
                .thenReturn(List.of(challenge("1", "Hello.", "Xin chào.")), List.of());
        assertThat(service.loadTranslatedChallenges("lesson")).hasSize(1);
        assertThat(service.loadTranslatedChallenges("lesson")).isEmpty();
        verifyNoInteractions(translator);
        verify(repository, never()).saveTranslationIfMissing(any(), any(), any());
    }

    @Test
    void savesPartialSuccessAndRetriesOnlyMissingTranslations() {
        List<ListenAndTypeExerciseChallenge> rows = List.of(
                challenge("1", "Hello.", null), challenge("2", "Thank you.", null));
        storedRows(rows);
        persistUpdates(rows);
        when(translator.translate(List.of("Hello.", "Thank you."), "vi"))
                .thenReturn(Map.of("Hello.", "Xin chào.", "Thank you.", " "));
        when(translator.translate(List.of("Thank you."), "vi"))
                .thenReturn(Map.of("Thank you.", "Cảm ơn."));

        assertThat(service.loadTranslatedChallenges("lesson")).extracting(ListenAndTypeExerciseChallenge::getTranslate)
                .containsExactly("Xin chào.", null);
        assertThat(service.loadTranslatedChallenges("lesson")).extracting(ListenAndTypeExerciseChallenge::getTranslate)
                .containsExactly("Xin chào.", "Cảm ơn.");
    }

    @Test
    void splitsByTextCountAndKeepsLaterBatchesWhenOneFails() {
        var rows = IntStream.rangeClosed(1, 101)
                .mapToObj(i -> challenge("id-" + i, "Sentence " + i, null)).toList();
        storedRows(rows);
        persistUpdates(rows);
        when(translator.translate(anyList(), eq("vi"))).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            if (texts.size() == 100) {
                throw new AppException(ErrorCode.TRANSLATION_FAILED);
            }
            assertThat(texts).containsExactly("Sentence 101");
            return Map.of("Sentence 101", "Câu 101");
        });

        var result = service.loadTranslatedChallenges("lesson");

        assertThat(result.getFirst().getTranslate()).isNull();
        assertThat(result.getLast().getTranslate()).isEqualTo("Câu 101");
        verify(translator, times(2)).translate(anyList(), eq("vi"));
    }

    @Test
    void splitsByCharacterLimitAndSkipsOversizedIndividualContent() {
        String first = "a".repeat(30_000);
        String second = "b".repeat(30_000);
        storedRows(List.of(challenge("1", first, null), challenge("2", second, null),
                challenge("3", "c".repeat(50_001), null)));
        when(translator.translate(List.of(first), "vi")).thenReturn(Map.of());
        when(translator.translate(List.of(second), "vi")).thenReturn(Map.of());

        assertThat(service.loadTranslatedChallenges("lesson")).hasSize(3);

        verify(translator, times(2)).translate(anyList(), eq("vi"));
        verify(repository, never()).saveTranslationIfMissing(any(), any(), any());
    }

    @Test
    void concurrentRequestsForSameLessonReuseFirstRequestsTranslations() throws Exception {
        var rows = List.of(challenge("1", "Hello.", null));
        storedRows(rows);
        persistUpdates(rows);
        CountDownLatch translating = new CountDownLatch(1);
        CountDownLatch releaseTranslation = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        when(translator.translate(List.of("Hello."), "vi")).thenAnswer(invocation -> {
            translating.countDown();
            assertThat(releaseTranslation.await(5, TimeUnit.SECONDS)).isTrue();
            return Map.of("Hello.", "Xin chào.");
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
                releaseTranslation.countDown();
                assertThat(first.get(5, TimeUnit.SECONDS).getFirst().getTranslate()).isEqualTo("Xin chào.");
                assertThat(second.get(5, TimeUnit.SECONDS).getFirst().getTranslate()).isEqualTo("Xin chào.");
            } finally {
                releaseTranslation.countDown();
            }
        }
        verify(translator, times(1)).translate(anyList(), eq("vi"));
    }

    private void storedRows(List<ListenAndTypeExerciseChallenge> rows) {
        when(repository.findByListenExerciseIdOrderByPositionAsc("lesson")).thenReturn(rows);
    }

    private void persistUpdates(List<ListenAndTypeExerciseChallenge> rows) {
        when(repository.saveTranslationIfMissing(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            rows.stream().filter(row -> row.getId().equals(invocation.getArgument(0)))
                    .findFirst().orElseThrow().setTranslate(invocation.getArgument(2));
            return 1;
        });
    }

    private ListenAndTypeExerciseChallenge challenge(String id, String content, String translation) {
        var challenge = new ListenAndTypeExerciseChallenge();
        challenge.setId(id);
        challenge.setListenExerciseId("lesson");
        challenge.setContent(content);
        challenge.setSolution("Different answer " + id);
        challenge.setTranslate(translation);
        return challenge;
    }
}
