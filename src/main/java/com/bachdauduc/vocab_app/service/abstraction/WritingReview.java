package com.bachdauduc.vocab_app.service.abstraction;

public interface WritingReview {
    String generateReview(String exerciseId, String userId, String userAnswer);
}