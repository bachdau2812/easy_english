package com.bachdauduc.vocab_app.dto.request.uservocabulary;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserSearchHistoryRequest {
    @NotBlank
    String userId;

    @NotBlank
    String wordId;
}
