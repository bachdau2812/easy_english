package com.bachdauduc.vocab_app.dto.response.learningresource;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IeltsReadingSourceResponse {
    String id;
    String name;
    String title;
    String categoryId;
    String content;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
