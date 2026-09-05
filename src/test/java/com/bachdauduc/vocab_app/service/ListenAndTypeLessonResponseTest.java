package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.entity.ListenAndTypeExerciseChallenge;
import com.bachdauduc.vocab_app.entity.ListenExercise;
import com.bachdauduc.vocab_app.repository.ListenExerciseRepository;
import com.bachdauduc.vocab_app.repository.UserInfoRepository;
import com.bachdauduc.vocab_app.repository.UserVocabAttemptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListenAndTypeLessonResponseTest {
    @Mock UserInfoRepository userInfoRepository;
    @Mock ListenExerciseRepository listenExerciseRepository;
    @Mock UserVocabAttemptRepository userVocabAttemptRepository;
    @Mock ListenAndTypeTranslationService listenAndTypeTranslationService;
    @InjectMocks ExerciseService service;

    @Test
    void lessonReturnsAllTranslationsAndPreservesCompletionAndSolution() throws Exception {
        when(userInfoRepository.existsById("user")).thenReturn(true);
        var lesson = new ListenExercise();
        lesson.setLessonId("lesson");
        when(listenExerciseRepository.findById("lesson")).thenReturn(Optional.of(lesson));
        var challenge = new ListenAndTypeExerciseChallenge();
        challenge.setId("challenge");
        challenge.setSolution("Hello.");
        challenge.setTranslate("Xin chào.");
        var untranslated = new ListenAndTypeExerciseChallenge();
        untranslated.setId("untranslated");
        untranslated.setTranslate("   ");
        when(listenAndTypeTranslationService.loadTranslatedChallenges("lesson"))
                .thenReturn(List.of(challenge, untranslated));
        when(userVocabAttemptRepository.findCompletedListenAndTypeChallengeIds("user", "lesson"))
                .thenReturn(List.of("challenge"));

        var result = service.getListenAndTypeLesson("user", "lesson");

        assertThat(result.getChallenges()).hasSize(2);
        assertThat(result.getChallenges().getFirst().getSolution()).isEqualTo("Hello.");
        assertThat(result.getChallenges().getFirst().getTranslate()).isEqualTo("Xin chào.");
        assertThat(result.getChallenges().getFirst().getIsDone()).isTrue();
        assertThat(result.getChallenges().getLast().getTranslate()).isNull();
        assertThat(result.getChallenges().getLast().getIsDone()).isFalse();
        var json = new ObjectMapper().valueToTree(result);
        assertThat(json.path("challenges").get(0).path("translate").asText()).isEqualTo("Xin chào.");
    }

    @Test
    void invalidLessonNeverTriggersTranslation() {
        when(userInfoRepository.existsById("user")).thenReturn(true);
        assertThatThrownBy(() -> service.getListenAndTypeLesson("user", "missing"))
                .isInstanceOf(com.bachdauduc.vocab_app.exception.AppException.class);
        verifyNoInteractions(listenAndTypeTranslationService);
    }
}
