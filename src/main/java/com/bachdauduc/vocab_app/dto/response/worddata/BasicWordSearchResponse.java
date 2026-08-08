package com.bachdauduc.vocab_app.dto.response.worddata;

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
public class BasicWordSearchResponse {
    String id;
    String word;
    String normalizedWord;
    String pos;
    String lang;
    String langCode;
    String wordSource;
    String otherSource;
    String certLevel;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}