package com.bachdauduc.vocab_app.dto.request.wordinfo;

import jakarta.validation.constraints.NotBlank;
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
public class InsertWordIdiomTranslationRequest {
    @NotBlank
    String idiomId;

    String idiom;
    String definition;
    String definitionGpt;
    String example;
    String example2;

    @NotBlank
    String langCode;

    Integer reviewStatus;
}
