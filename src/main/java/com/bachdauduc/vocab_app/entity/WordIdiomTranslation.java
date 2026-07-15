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
@Table(name = "word_idiom_trans")
public class WordIdiomTranslation {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "idiom_id", length = 36, nullable = false)
    private String idiomId;

    @Column(name = "idiom", columnDefinition = "TEXT")
    private String idiom;

    @Column(name = "definition", columnDefinition = "TEXT")
    private String definition;

    @Column(name = "example", columnDefinition = "TEXT")
    private String example;

    @Column(name = "example_2", columnDefinition = "TEXT")
    private String example2;

    @Column(name = "review_status", nullable = false)
    private Integer reviewStatus;

    @Column(name = "lang_code", length = 10, nullable = false)
    private String langCode;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "definition_gpt", columnDefinition = "TEXT")
    private String definitionGpt;
}
