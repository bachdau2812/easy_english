package com.bachdauduc.vocab_app.dto.response.uservocabulary;

import com.bachdauduc.vocab_app.constant.UserVocabularyInfoType;
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
public class UserVocabularyInfoResponse {
    String userId;
    UserVocabularyInfoType infoType;
    Long totalQuantity;
    List<UserVocabularyLevelQuantityResponse> quantityByLevels;
    Long reviewQuantity;
}
