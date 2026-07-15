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
public class WordResponse {
    String wordId;
    String word;
    String normalizedWord;
    String pos;
    String certLevel;
    String lang;
    String langCode;
    String wordSource;
    String otherSource;
    List<String> categories;
    List<WordSoundResponse> sounds;
    List<WordSenseResponse> senses;
    List<WordIdiomResponse> idioms;
    List<WordFormResponse> forms;
    WordRelationResponse relation;
}
