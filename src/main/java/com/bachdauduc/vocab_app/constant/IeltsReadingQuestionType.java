package com.bachdauduc.vocab_app.constant;

import java.util.Arrays;

public enum IeltsReadingQuestionType {
    // Matching task: match a statement to a person, entity, feature, or label.
    MATCHING_FEATURES("matching_features"),

    // Matching task: match headings to paragraphs or sections.
    MATCHING_HEADINGS("matching_headings"),

    // Matching task: match statements to paragraph labels containing the information.
    MATCHING_INFORMATION("matching_information"),

    // Multiple-choice task: select more than one correct option.
    MULTIPLE_CHOICE_MULTIPLE("multiple_choice_multiple"),

    // Multiple-choice task: select one correct option.
    MULTIPLE_CHOICE_SINGLE("multiple_choice_single"),

    // Completion task: fill sentence gaps with words or numbers from the passage.
    SENTENCE_COMPLETION("sentence_completion"),

    // Short-answer task: answer a direct question with words or numbers from the passage.
    SHORT_ANSWER("short_answer"),

    // Completion task: fill summary gaps with passage words or a shared option bank.
    SUMMARY_COMPLETION("summary_completion"),

    // Identifying-information task: choose TRUE, FALSE, or NOT GIVEN for factual statements.
    TRUE_FALSE_NOT_GIVEN("true_false_not_given"),

    // Identifying-writer-views task: choose YES, NO, or NOT GIVEN for claims or opinions.
    YES_NO_NOT_GIVEN("yes_no_not_given");

    private final String value;

    IeltsReadingQuestionType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static IeltsReadingQuestionType fromValue(String value) {
        return Arrays.stream(values())
                .filter(type -> type.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported IELTS reading question type: " + value));
    }
}
