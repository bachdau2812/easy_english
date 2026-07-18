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
@Table(name = "ielts_writing_exercise")
public class IeltsWritingExercise {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "problem", columnDefinition = "LONGTEXT", nullable = false)
    private String problem;

    @Column(name = "problem_topic", nullable = false)
    private String problemTopic;

    @Column(name = "task_type", nullable = false)
    private Integer taskType;

    @Column(name = "evaluation_prompt", columnDefinition = "LONGTEXT", nullable = false)
    private String evaluationPrompt;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "image_description", columnDefinition = "LONGTEXT")
    private String imageDescription;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
