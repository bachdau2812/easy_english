package com.bachdauduc.vocab_app.dto.response.uservocabulary;

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
public class UserSearchHistoryResponse {
    String id;
    String userId;
    String wordId;
    String word;
    LocalDateTime searchedAt;
}
