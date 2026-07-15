package com.bachdauduc.vocab_app.dto.request.wordinfo;

import jakarta.validation.constraints.NotBlank;
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
public class InsertWordRelationRequest {
    @NotBlank
    String wordId;

    List<String> synonyms;
    List<String> antonyms;
    List<String> derived;
    List<String> coordinateTerms;
    List<String> formOf;
    List<String> altOf;
}
