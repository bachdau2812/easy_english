package com.bachdauduc.vocab_app.dto.response.exercise;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ListenAndTypeLessonResponse {
    String userId;
    String lessonId;
    String title;
    String categoryName;
    String fullDocument;
    String speechToTextLangCode;
    String audioUrl;
    String learningResourceType;
    List<String> completedChallengeIds;
    List<ChallengeResponse> challenges;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ChallengeResponse {
        String id;
        Integer position;
        String content;
        String jsonContent;
        String solution;
        String translate;
        BigDecimal timeStart;
        BigDecimal timeEnd;
        String hints;
        String audioSrc;
        Boolean isDone;
    }
}
