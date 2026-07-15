package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.WordForm;
import com.bachdauduc.vocab_app.repository.projection.WordFormProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WordFormRepository extends JpaRepository<WordForm, String> {
    List<WordForm> findByWordId(String wordId);

    @Query(value = """
            SELECT
                w.id AS wordId,
                w.word AS word,
                w.pos AS pos,
                w.cert_level AS certLevel,
                wf.form AS form,
                wf.tags AS tags
            FROM words w
            JOIN word_forms wf ON w.id = wf.word_id
            WHERE w.id = :wordId
            """, nativeQuery = true)
    List<WordFormProjection> findWordForms(@Param("wordId") String wordId);
}
