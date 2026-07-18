package com.bachdauduc.vocab_app.dto.response.learningresource;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class IeltsReadingQuizResponse {
    Quiz quiz;
    String id;
    @Builder.Default
    List<String> completedQuestionIds = List.of();

    public IeltsReadingQuizResponse withoutCompletedQuestionIds() {
        return IeltsReadingQuizResponse.builder()
                .quiz(quiz)
                .id(id)
                .completedQuestionIds(List.of())
                .build();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Quiz {
        String title;
        String module;
        PassageAnalysis passageAnalysis;
        @Builder.Default
        List<QuestionGroup> questionGroups = List.of();
        String id;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class PassageAnalysis {
        Integer paragraphCount;
        String textType;
        Boolean writerViewPresent;
        Boolean processPresent;
        Boolean multiEntityPresent;
        @Builder.Default
        List<String> selectedQuestionTypes = List.of();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class QuestionGroup {
        String groupId;
        String questionType;
        String instruction;
        Integer questionNumberStart;
        Integer questionNumberEnd;
        String context;
        Boolean allowOptionReuse;
        String wordLimit;
        @Builder.Default
        List<String> sourceParagraphIds = List.of();
        @Builder.Default
        List<String> sharedOptions = List.of();
        @Builder.Default
        List<Question> questions = List.of();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Question {
        String questionId;
        Integer number;
        String stem;
        @Builder.Default
        List<String> options = List.of();
        @Builder.Default
        List<String> answer = List.of();
        String sourceParagraphId;
        String evidenceQuote;
        String explanation;
        String difficulty;
        String skill;
    }
}
