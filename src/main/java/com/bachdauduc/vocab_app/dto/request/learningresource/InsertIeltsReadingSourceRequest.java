package com.bachdauduc.vocab_app.dto.request.learningresource;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InsertIeltsReadingSourceRequest {
    @NotBlank
    String name;

    @NotBlank
    String title;

    @NotBlank
    String categorySlug;

    @NotBlank
    String content;
}
