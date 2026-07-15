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
@Table(name = "words")
public class Word {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "word", columnDefinition = "TEXT", nullable = false)
    private String word;

    @Column(name = "normalized_word", columnDefinition = "TEXT", nullable = false)
    private String normalizedWord;

    @Column(name = "pos", length = 50)
    private String pos;

    @Column(name = "lang", length = 50)
    private String lang;

    @Column(name = "lang_code", length = 10)
    private String langCode;

    @Column(name = "word_source", length = 50, nullable = false)
    private String wordSource;

    @Column(name = "other_source", length = 100)
    private String otherSource;

    @Column(name = "cert_level", length = 20)
    private String certLevel;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
