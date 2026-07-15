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
@Table(name = "word_sense_localizations")
public class WordSenseLocalization {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "sense_id", length = 36)
    private String senseId;

    @Column(name = "word_id", length = 36, nullable = false)
    private String wordId;

    @Column(name = "lang_code", length = 10, nullable = false)
    private String langCode;

    @Column(name = "short_meaning", columnDefinition = "TEXT")
    private String shortMeaning;

    @Column(name = "full_localized_definition", columnDefinition = "TEXT")
    private String fullLocalizedDefinition;

    @Column(name = "source", length = 50, nullable = false)
    private String source;

    @Column(name = "review_status", nullable = false)
    private Integer reviewStatus;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
