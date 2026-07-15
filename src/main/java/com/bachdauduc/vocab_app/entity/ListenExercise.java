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
@Table(name = "listen_exercise")
public class ListenExercise {
    @Id
    @Column(name = "lesson_id", length = 36, nullable = false)
    private String lessonId;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "category_id", length = 36)
    private String categoryId;

    @Column(name = "sub_category", length = 255)
    private String subCategory;

    @Column(name = "full_document", columnDefinition = "LONGTEXT")
    private String fullDocument;

    @Column(name = "speech_to_text_lang_code", length = 20)
    private String speechToTextLangCode;

    @Column(name = "audio_url", columnDefinition = "TEXT")
    private String audioUrl;

    @Column(name = "learning_resource_type", length = 100)
    private String learningResourceType;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
