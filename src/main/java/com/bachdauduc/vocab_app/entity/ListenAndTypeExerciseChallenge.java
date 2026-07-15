package com.bachdauduc.vocab_app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "listen_and_type_exercise_challenges")
public class ListenAndTypeExerciseChallenge {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "listen_exercise_id", length = 36, nullable = false)
    private String listenExerciseId;

    @Column(name = "position")
    private Integer position;

    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "json_content", columnDefinition = "LONGTEXT")
    private String jsonContent;

    @Column(name = "solution", columnDefinition = "LONGTEXT")
    private String solution;

    @Column(name = "time_start", precision = 10, scale = 3)
    private BigDecimal timeStart;

    @Column(name = "time_end", precision = 10, scale = 3)
    private BigDecimal timeEnd;

    @Column(name = "hints", columnDefinition = "LONGTEXT")
    private String hints;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "audio_src", columnDefinition = "TEXT")
    private String audioSrc;
}
