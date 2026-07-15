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
@Table(name = "word_examples")
public class WordExample {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "word_id", length = 36, nullable = false)
    private String wordId;

    @Column(name = "sense_id", length = 36)
    private String senseId;

    @Column(name = "text", columnDefinition = "TEXT")
    private String text;

    @Column(name = "example_type", length = 50)
    private String exampleType;

    @Column(name = "source_ref", columnDefinition = "TEXT")
    private String sourceRef;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
