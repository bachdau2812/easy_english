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
@Table(name = "ielts_reading_questions")
public class IeltsReadingQuestion {
    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "group_id", length = 36, nullable = false)
    private String groupId;

    @Column(name = "reading_source_id", length = 36, nullable = false)
    private String readingSourceId;

    @Column(name = "question_number", nullable = false)
    private Integer questionNumber;

    @Column(name = "stem", columnDefinition = "LONGTEXT")
    private String stem;

    @Column(name = "options", columnDefinition = "LONGTEXT")
    private String options;

    @Column(name = "answer", columnDefinition = "LONGTEXT")
    private String answer;

    @Column(name = "source_paragraph_id", length = 50)
    private String sourceParagraphId;

    @Column(name = "evidence_quote", columnDefinition = "LONGTEXT")
    private String evidenceQuote;

    @Column(name = "explanation", columnDefinition = "LONGTEXT")
    private String explanation;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
