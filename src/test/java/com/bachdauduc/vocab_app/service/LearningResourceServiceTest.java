package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.constant.ExerciseType;

import com.bachdauduc.vocab_app.dto.response.learningresource.IeltsReadingQuizResponse;
import com.bachdauduc.vocab_app.dto.response.learningresource.IeltsWritingProblemSummaryResponse;
import com.bachdauduc.vocab_app.entity.IeltsReadingSource;
import com.bachdauduc.vocab_app.entity.UserVocabAttempt;
import com.bachdauduc.vocab_app.properties.RedisKeyProperties;
import com.bachdauduc.vocab_app.repository.CategoryRepository;
import com.bachdauduc.vocab_app.repository.IeltsReadingQuizGroupRepository;
import com.bachdauduc.vocab_app.repository.IeltsReadingSourceRepository;
import com.bachdauduc.vocab_app.repository.IeltsWritingExerciseRepository;
import com.bachdauduc.vocab_app.repository.IeltsWritingReferenceRepository;
import com.bachdauduc.vocab_app.repository.UserVocabAttemptRepository;
import com.bachdauduc.vocab_app.repository.projection.IeltsReadingQuizRowProjection;
import com.bachdauduc.vocab_app.repository.projection.IeltsWritingProblemSummaryProjection;
import com.bachdauduc.vocab_app.service.abstraction.WritingReview;
import com.bachdauduc.vocab_app.utils.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningResourceServiceTest {
    @Mock
    IeltsReadingSourceRepository ieltsReadingSourceRepository;
    @Mock
    CategoryRepository categoryRepository;
    @Mock
    IeltsReadingQuizGroupRepository ieltsReadingQuizGroupRepository;
    @Mock
    IeltsWritingExerciseRepository ieltsWritingExerciseRepository;
    @Mock
    IeltsWritingReferenceRepository ieltsWritingReferenceRepository;
    @Mock
    UserVocabAttemptRepository userVocabAttemptRepository;
    @Mock
    RedisTemplate<String, String> redisTemplate;
    @Mock
    ValueOperations<String, String> valueOperations;
    @Mock
    RedisKeyProperties redisKeyProperties;
    @Mock
    WritingReview writingReview;

    LearningResourceService service;

    @BeforeEach
    void setUp() {
        service = new LearningResourceService(
                ieltsReadingSourceRepository,
                categoryRepository,
                ieltsReadingQuizGroupRepository,
                ieltsWritingExerciseRepository,
                ieltsWritingReferenceRepository,
                userVocabAttemptRepository,
                redisTemplate,
                redisKeyProperties,
                writingReview
        );
    }

    @Test
    void getIeltsReadingQuizLoadsFromDbCachesQuizAndAddsCompletedQuestionIds() {
        String readingId = "reading-1";
        String userId = "user-1";
        String cacheKey = "reading_quiz:reading-1";
        IeltsReadingSource source = new IeltsReadingSource();
        source.setId(readingId);
        source.setTitle("Volcanic activity");
        source.setContent("A. One paragraph");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisKeyProperties.readingQuizKey(readingId)).thenReturn(cacheKey);
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(ieltsReadingSourceRepository.findById(readingId)).thenReturn(Optional.of(source));
        when(ieltsReadingQuizGroupRepository.findQuizRowsByReadingSourceId(readingId)).thenReturn(List.of(
                row("g1", "true_false_not_given", 1, "Instruction", 1, 2, null, "A", null,
                        "q1", 1, "Statement one", null, "[\"TRUE\"]", "A", "Evidence", "Explanation"),
                row("g1", "true_false_not_given", 1, "Instruction", 1, 2, null, "A", null,
                        "q2", 2, "Statement two", null, "[\"FALSE\"]", "A", "Evidence 2", "Explanation 2")
        ));
        when(userVocabAttemptRepository.findCompletedAttemptIds(userId, List.of("q1", "q2")))
                .thenReturn(List.of("q2"));

        IeltsReadingQuizResponse response = service.getIeltsReadingQuiz(readingId, userId);

        assertThat(response.getId()).isEqualTo(readingId);
        assertThat(response.getCompletedQuestionIds()).containsExactly("q2");
        assertThat(response.getQuiz().getTitle()).isEqualTo("Volcanic activity");
        assertThat(response.getQuiz().getQuestionGroups()).hasSize(1);
        assertThat(response.getQuiz().getQuestionGroups().getFirst().getQuestions()).hasSize(2);
        assertThat(response.getQuiz().getQuestionGroups().getFirst().getQuestions().getFirst().getAnswer())
                .containsExactly("TRUE");
        verify(valueOperations).set(eq(cacheKey), eq(RedisUtil.serialize(response.withoutCompletedQuestionIds())));
    }

    @Test
    void getIeltsReadingQuizUsesCachedQuizAndStillLoadsCompletedQuestionIdsForUser() {
        String readingId = "reading-1";
        String userId = "user-1";
        String cacheKey = "reading_quiz:reading-1";
        IeltsReadingQuizResponse cached = IeltsReadingQuizResponse.builder()
                .id(readingId)
                .quiz(IeltsReadingQuizResponse.Quiz.builder()
                        .id(readingId)
                        .title("Cached title")
                        .module("academic_reading")
                        .questionGroups(List.of(IeltsReadingQuizResponse.QuestionGroup.builder()
                                .groupId("g1")
                                .questionType("sentence_completion")
                                .questions(List.of(IeltsReadingQuizResponse.Question.builder()
                                        .questionId("q1")
                                        .number(1)
                                        .answer(List.of("answer"))
                                        .options(List.of())
                                        .build()))
                                .build()))
                        .build())
                .completedQuestionIds(List.of())
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisKeyProperties.readingQuizKey(readingId)).thenReturn(cacheKey);
        when(valueOperations.get(cacheKey)).thenReturn(RedisUtil.serialize(cached));
        when(userVocabAttemptRepository.findCompletedAttemptIds(userId, List.of("q1")))
                .thenReturn(List.of("q1"));

        IeltsReadingQuizResponse response = service.getIeltsReadingQuiz(readingId, userId);

        assertThat(response.getQuiz().getTitle()).isEqualTo("Cached title");
        assertThat(response.getCompletedQuestionIds()).containsExactly("q1");
        verify(ieltsReadingQuizGroupRepository, never()).findQuizRowsByReadingSourceId(readingId);
    }

    @Test
    void getIeltsWritingProblemsByTopicReturnsProblemSummariesWithDoneStatus() {
        when(ieltsWritingExerciseRepository.findProblemSummariesByTopic("Environment", "user-1"))
                .thenReturn(List.of(problem("p1", "Some problem")));

        List<IeltsWritingProblemSummaryResponse> response = service.getIeltsWritingProblemsByTopic("Environment", "user-1");

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getId()).isEqualTo("p1");
        assertThat(response.getFirst().getProblem()).isEqualTo("Some problem");
        assertThat(response.getFirst().getIsDone()).isTrue();
    }

    @Test
    void reviewIeltsWritingSavesAttemptWithGeneratedReview() {
        when(writingReview.generateReview("exercise-1", "user-1", "My essay"))
                .thenReturn("{\"overallBand\":7.0}");
        when(userVocabAttemptRepository.save(org.mockito.ArgumentMatchers.any(UserVocabAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String response = service.reviewIeltsWriting("exercise-1", "user-1", "My essay");

        assertThat(response).isEqualTo("{\"overallBand\":7.0}");
        ArgumentCaptor<UserVocabAttempt> captor = ArgumentCaptor.forClass(UserVocabAttempt.class);
        verify(userVocabAttemptRepository).save(captor.capture());
        UserVocabAttempt saved = captor.getValue();
        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getAttemptId()).isEqualTo("exercise-1");
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getUserAnswer()).isEqualTo("My essay");
        assertThat(saved.getReview()).isEqualTo("{\"overallBand\":7.0}");
    }

    @Test
    void getIeltsWritingAttemptHistoryReturnsAttemptRecordsForUserAndExercise() {
        UserVocabAttempt firstAttempt = new UserVocabAttempt();
        firstAttempt.setId("attempt-record-1");
        firstAttempt.setAttemptId("exercise-1");
        firstAttempt.setExerciseType(ExerciseType.IELTS_WRITING_REVIEW);
        UserVocabAttempt secondAttempt = new UserVocabAttempt();
        secondAttempt.setId("attempt-record-2");
        secondAttempt.setAttemptId("exercise-1");
        secondAttempt.setExerciseType(ExerciseType.IELTS_WRITING_REVIEW);
        when(userVocabAttemptRepository.findByUserIdAndAttemptIdAndExerciseTypeOrderByCreatedAtDescIdDesc(
                "user-1",
                "exercise-1",
                ExerciseType.IELTS_WRITING_REVIEW
        )).thenReturn(List.of(firstAttempt, secondAttempt));

        List<UserVocabAttempt> response = service.getIeltsWritingAttemptHistory("user-1", "exercise-1");

        assertThat(response).containsExactly(firstAttempt, secondAttempt);
    }
    private IeltsReadingQuizRowProjection row(
            String groupId,
            String questionType,
            Integer groupOrder,
            String instruction,
            Integer questionNumberStart,
            Integer questionNumberEnd,
            String wordLimit,
            String groupSourceParagraphId,
            String sharedOptions,
            String questionId,
            Integer questionNumber,
            String stem,
            String options,
            String answer,
            String questionSourceParagraphId,
            String evidenceQuote,
            String explanation
    ) {
        return new IeltsReadingQuizRowProjection() {
            public String getGroupId() { return groupId; }
            public String getQuestionType() { return questionType; }
            public Integer getGroupOrder() { return groupOrder; }
            public String getInstruction() { return instruction; }
            public Integer getQuestionNumberStart() { return questionNumberStart; }
            public Integer getQuestionNumberEnd() { return questionNumberEnd; }
            public String getWordLimit() { return wordLimit; }
            public String getGroupSourceParagraphId() { return groupSourceParagraphId; }
            public String getSharedOptions() { return sharedOptions; }
            public String getQuestionId() { return questionId; }
            public Integer getQuestionNumber() { return questionNumber; }
            public String getStem() { return stem; }
            public String getOptions() { return options; }
            public String getAnswer() { return answer; }
            public String getQuestionSourceParagraphId() { return questionSourceParagraphId; }
            public String getEvidenceQuote() { return evidenceQuote; }
            public String getExplanation() { return explanation; }
        };
    }

    private IeltsWritingProblemSummaryProjection problem(String id, String problem) {
        return new IeltsWritingProblemSummaryProjection() {
            public String getId() { return id; }
            public String getProblem() { return problem; }
            public Long getDoneCount() { return 1L; }
        };
    }
}
