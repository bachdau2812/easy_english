package com.bachdauduc.vocab_app.dto.response.worddata;

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
public class WordExampleResponse {
    String wordExampleId;
    String senseId;
    String wordSenseLocalizationId;
    String wordId;
    String word;
    String pos;
    String certLevel;
    String sentence;
    String trans;
}
