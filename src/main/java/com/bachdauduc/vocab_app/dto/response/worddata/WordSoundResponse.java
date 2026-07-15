package com.bachdauduc.vocab_app.dto.response.worddata;

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
public class WordSoundResponse {
    String wordId;
    String ipa;
    List<String> tags;
    String soundSource;
    String oggUrl;
    String mp3Url;
    String enpr;
}
