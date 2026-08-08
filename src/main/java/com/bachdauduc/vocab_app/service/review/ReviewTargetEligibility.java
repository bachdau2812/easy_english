package com.bachdauduc.vocab_app.service.review;

import com.bachdauduc.vocab_app.constant.ExerciseType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public record ReviewTargetEligibility(
        String userVocabId,
        Set<ExerciseType> eligibleTypes
) {
    public ReviewTargetEligibility {
        EnumSet<ExerciseType> vocabTypes = EnumSet.noneOf(ExerciseType.class);
        if (eligibleTypes != null) {
            eligibleTypes.stream()
                    .filter(ExerciseType::isVocab)
                    .forEach(vocabTypes::add);
        }
        eligibleTypes = Collections.unmodifiableSet(vocabTypes);
    }
}
