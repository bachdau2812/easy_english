package com.bachdauduc.vocab_app.dto.response.uservocabulary;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserVocabularyStatisticResponse {
    String userId;
    LocalDate statisticDate;
    Long totalAttempts;
    Long correctQuizAttempt;
    Long wrongQuizAttempt;
    Long totalUniqueVocab;
    Long correctUniqueVocab;
    Long wrongUniqueVocab;
    Long wrongCountVocab;
    List<WrongVocabResponse> wrongVocabIds;
    List<WrongVocabResponse> mostWrongVocabIds;
}
