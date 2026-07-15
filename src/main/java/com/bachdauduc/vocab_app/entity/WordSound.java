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
@Table(name = "word_sounds")
public class WordSound {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "word_id", length = 36, nullable = false)
    private String wordId;

    @Column(name = "ipa")
    private String ipa;

    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;

    @Column(name = "sound_source", length = 50, nullable = false)
    private String soundSource;

    @Column(name = "ogg_url", columnDefinition = "TEXT")
    private String oggUrl;

    @Column(name = "mp3_url", columnDefinition = "TEXT")
    private String mp3Url;

    @Column(name = "enpr")
    private String enpr;
}
