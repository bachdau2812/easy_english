package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.IeltsReadingQuizGroup;
import com.bachdauduc.vocab_app.repository.projection.IeltsReadingQuizRowProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IeltsReadingQuizGroupRepository extends JpaRepository<IeltsReadingQuizGroup, String> {
    @Query(value = """
            SELECT
                g.id AS groupId,
                g.quiz_type AS questionType,
                g.group_order AS groupOrder,
                g.instruction AS instruction,
                g.question_number_start AS questionNumberStart,
                g.question_number_end AS questionNumberEnd,
                g.word_limit AS wordLimit,
                g.source_paragraph_id AS groupSourceParagraphId,
                g.shared_options AS sharedOptions,
                q.id AS questionId,
                q.question_number AS questionNumber,
                q.stem AS stem,
                q.options AS options,
                q.answer AS answer,
                q.source_paragraph_id AS questionSourceParagraphId,
                q.evidence_quote AS evidenceQuote,
                q.explanation AS explanation
            FROM ielts_reading_quiz_groups g
            JOIN ielts_reading_questions q
                ON q.group_id = g.id
                AND q.reading_source_id = g.reading_source_id
            WHERE g.reading_source_id = :readingSourceId
            ORDER BY g.group_order ASC, q.question_number ASC, q.id ASC
            """, nativeQuery = true)
    List<IeltsReadingQuizRowProjection> findQuizRowsByReadingSourceId(@Param("readingSourceId") String readingSourceId);
}
