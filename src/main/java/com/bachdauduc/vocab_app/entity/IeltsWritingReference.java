package com.bachdauduc.vocab_app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "ielts_writing_reference")
public class IeltsWritingReference {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "ielts_writing_exercise_id", length = 36, nullable = false)
    private String ieltsWritingExerciseId;

    @Column(name = "essay", columnDefinition = "LONGTEXT", nullable = false)
    private String essay;

    @Column(name = "band", length = 20)
    private String band;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
