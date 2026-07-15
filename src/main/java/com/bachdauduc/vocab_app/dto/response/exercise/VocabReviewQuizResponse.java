package com.bachdauduc.vocab_app.dto.response.exercise;

import com.bachdauduc.vocab_app.constant.ExerciseType;
import com.bachdauduc.vocab_app.dto.response.worddata.WordExampleResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordSenseResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordSoundResponse;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VocabReviewQuizResponse {
    String wordId;
    String userVocabId;
    String word;
    String pos;
    WordSoundResponse sound;
    WordExampleResponse example;
    WordSenseResponse sense;
    WordSenseResponse wordSense;
    ExerciseType exerciseType;
    String correctAnswer;
    List<String> listAnswers;
    Map<Integer, String> metadata;
    String maskedWord;
    String audioUrl;
    Integer missIndex;
    String sentence;
    String trans;
}
