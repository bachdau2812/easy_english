package com.bachdauduc.vocab_app.dto.response.uservocabulary;

import com.bachdauduc.vocab_app.dto.response.worddata.WordResponse;
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
public class UserVocabularySearchResponse {
    UserVocabularyResponse userVocabulary;
    WordResponse word;
}
