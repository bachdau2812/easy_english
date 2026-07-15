package com.bachdauduc.vocab_app.dto.response.uservocabulary;

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
public class WrongVocabResponse {
    String userVocabId;
    String word;
    Long wrongCount;
}
