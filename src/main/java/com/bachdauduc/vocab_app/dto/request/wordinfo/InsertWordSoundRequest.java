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
public class InsertWordSoundRequest {
    @NotBlank
    String wordId;

    String ipa;
    List<String> tags;

    @NotBlank
    String soundSource;

    String oggUrl;
    String mp3Url;
    String enpr;
}
