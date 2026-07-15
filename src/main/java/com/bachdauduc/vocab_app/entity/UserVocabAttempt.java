package com.bachdauduc.vocab_app.entity;

import com.bachdauduc.vocab_app.constant.ExerciseType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_vocab_attempts")
public class UserVocabAttempt {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "attempt_id", length = 36)
    private String attemptId;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "user_vocab_id", length = 36)
    private String userVocabId;

    @Column(name = "exercise_type", length = 100, nullable = false)
    @Enumerated(EnumType.STRING)
    private ExerciseType exerciseType;

    @Column(name = "user_answer", columnDefinition = "TEXT")
    private String userAnswer;

    @Column(name = "is_correct", nullable = false)
    private Boolean correct;

    @Column(name = "replay_count", nullable = false)
    private Integer replayCount;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
