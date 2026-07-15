package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.WordIdiom;
import com.bachdauduc.vocab_app.repository.projection.WordIdiomProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WordIdiomRepository extends JpaRepository<WordIdiom, String> {
    List<WordIdiom> findByWordId(String wordId);

    @Query(value = """
            SELECT
                w.id AS wordId,
                w.word AS word,
                w.pos AS pos,
                w.cert_level AS certLevel,
                wi.idiom AS idiom,
                wi.definition AS definition,
                wi.example AS example,
                wi.example2 AS example2,
                NULL AS transIdiom,
                NULL AS transDefinition,
                NULL AS transExample,
                NULL AS transExample2
            FROM words w
            JOIN word_idioms wi ON w.id = wi.word_id
            WHERE w.id = :wordId
            """, nativeQuery = true)
    List<WordIdiomProjection> findWordIdioms(@Param("wordId") String wordId);

    @Query(value = """
            SELECT
                w.id AS wordId,
                w.word AS word,
                w.pos AS pos,
                w.cert_level AS certLevel,
                wi.idiom AS idiom,
                wi.definition AS definition,
                wi.example AS example,
                wi.example2 AS example2,
                wit.idiom AS transIdiom,
                wit.definition AS transDefinition,
                wit.example AS transExample,
                wit.example_2 AS transExample2
            FROM words w
            JOIN word_idioms wi ON w.id = wi.word_id
            LEFT JOIN word_idiom_trans wit
                ON wi.id = wit.idiom_id
                AND wit.lang_code = :transLangCode
            WHERE w.id = :wordId
            """, nativeQuery = true)
    List<WordIdiomProjection> findWordIdiomsWithTrans(
            @Param("wordId") String wordId,
            @Param("transLangCode") String transLangCode
    );
}
