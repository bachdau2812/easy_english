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
@Table(name = "word_forms")
public class WordForm {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "word_id", length = 36, nullable = false)
    private String wordId;

    @Column(name = "form", columnDefinition = "TEXT", nullable = false)
    private String form;

    @Column(name = "normalized_form", columnDefinition = "TEXT", nullable = false)
    private String normalizedForm;

    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;
}
