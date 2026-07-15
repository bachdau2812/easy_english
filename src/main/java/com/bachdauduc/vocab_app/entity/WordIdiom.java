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
@Table(name = "word_idioms")
public class WordIdiom {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "word_id", length = 36, nullable = false)
    private String wordId;

    @Column(name = "idiom", length = 500, nullable = false)
    private String idiom;

    @Column(name = "definition", columnDefinition = "TEXT")
    private String definition;

    @Column(name = "definition_gpt", columnDefinition = "TEXT")
    private String definitionGpt;

    @Column(name = "example", columnDefinition = "TEXT")
    private String example;

    @Column(name = "example2", columnDefinition = "TEXT")
    private String example2;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "sense_id", length = 50)
    private String senseId;

    @Column(name = "idiom_source", length = 50)
    private String idiomSource;
}
