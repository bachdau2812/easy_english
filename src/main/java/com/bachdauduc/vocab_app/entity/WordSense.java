package com.bachdauduc.vocab_app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "word_senses")
public class WordSense {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "word_id", length = 36, nullable = false)
    private String wordId;

    @Column(name = "definition", columnDefinition = "TEXT")
    private String definition;

    @Column(name = "synonyms", columnDefinition = "TEXT")
    private String synonyms;

    @Column(name = "antonyms", columnDefinition = "TEXT")
    private String antonyms;

    @Column(name = "derived", columnDefinition = "TEXT")
    private String derived;

    @Column(name = "coordinate_terms", columnDefinition = "TEXT")
    private String coordinateTerms;

    @Column(name = "form_of", columnDefinition = "TEXT")
    private String formOf;

    @Column(name = "alt_of", columnDefinition = "TEXT")
    private String altOf;
}
