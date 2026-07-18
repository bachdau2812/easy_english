package com.bachdauduc.vocab_app.repository.projection;

public interface IeltsReadingQuizRowProjection {
    String getGroupId();
    String getQuestionType();
    Integer getGroupOrder();
    String getInstruction();
    Integer getQuestionNumberStart();
    Integer getQuestionNumberEnd();
    String getWordLimit();
    String getGroupSourceParagraphId();
    String getSharedOptions();
    String getQuestionId();
    Integer getQuestionNumber();
    String getStem();
    String getOptions();
    String getAnswer();
    String getQuestionSourceParagraphId();
    String getEvidenceQuote();
    String getExplanation();
}
