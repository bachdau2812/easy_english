package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.WordRelation;
import com.bachdauduc.vocab_app.repository.projection.WordRelationProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WordRelationRepository extends JpaRepository<WordRelation, String> {
    List<WordRelation> findByWordId(String wordId);

    @Query(value = """
            SELECT
                w.id AS wordId,
                w.word AS word,
                w.pos AS pos,
                w.cert_level AS certLevel,
                wr.synonyms AS synonyms,
                wr.antonyms AS antonyms,
                wr.derived AS derived,
                wr.coordinate_terms AS coordinateTerms,
                wr.form_of AS formOf,
                wr.alt_of AS altOf
            FROM words w
            JOIN word_relations wr ON w.id = wr.word_id
            WHERE w.id = :wordId
            """, nativeQuery = true)
    List<WordRelationProjection> findWordRelations(@Param("wordId") String wordId);
}
