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
@Table(name = "listening_category")
public class ListeningCategory {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "category_name", nullable = false)
    private String categoryName;

    @Column(name = "slug")
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
