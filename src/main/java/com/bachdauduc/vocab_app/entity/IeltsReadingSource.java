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
@Table(name = "ielts_reading_sources")
public class IeltsReadingSource {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "name")
    private String name;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "category_id", length = 36)
    private String categoryId;

    @Column(name = "content", columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
