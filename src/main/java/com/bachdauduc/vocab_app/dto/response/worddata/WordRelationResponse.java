package com.bachdauduc.vocab_app.dto.response.worddata;

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
public class WordRelationResponse {
    String wordId;
    String word;
    String pos;
    String certLevel;
    List<String> synonyms;
    List<String> antonyms;
    List<String> derived;
    List<String> coordinateTerms;
    List<String> formOf;
    List<String> altOf;
}
