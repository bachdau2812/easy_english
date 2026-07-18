package com.bachdauduc.vocab_app.dto.request.learningresource;

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
public class IeltsWritingReviewRequest {
    @NotBlank
    String exerciseId;

    @NotBlank
    String userId;

    @NotBlank
    String userAnswer;
}