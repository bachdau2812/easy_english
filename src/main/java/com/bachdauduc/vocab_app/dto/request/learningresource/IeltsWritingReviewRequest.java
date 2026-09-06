package com.bachdauduc.vocab_app.dto.request.learningresource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class IeltsWritingReviewRequest {
    @NotBlank
    String exerciseId;

    @Deprecated
    String userId;

    @NotBlank
    @Size(max = 20_000)
    String userAnswer;
}
