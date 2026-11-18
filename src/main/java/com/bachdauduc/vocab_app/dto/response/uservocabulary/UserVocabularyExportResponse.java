package com.bachdauduc.vocab_app.dto.response.uservocabulary;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonPropertyOrder({
        "meaning_practice", "word", "pos", "word_sense", "word_practice", "level", "langcode", "examples"
})
public class UserVocabularyExportResponse {
    @JsonProperty("meaning_practice")
    String meaningPractice;
    String word;
    String pos;
    @JsonProperty("word_sense")
    String wordSense;
    @JsonProperty("word_practice")
    String wordPractice;
    Integer level;
    @JsonProperty("langcode")
    String langCode;
    String examples;
}
