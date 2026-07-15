package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.WordExample;
import com.bachdauduc.vocab_app.repository.projection.WordExampleProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WordExampleRepository extends JpaRepository<WordExample, String> {
    List<WordExample> findByWordId(String wordId);

    List<WordExample> findByWordIdAndSenseId(String wordId, String senseId);

    @Query(value = """
            SELECT
                we.id AS wordExampleId,
                ws.id AS senseId,
                NULL AS wordSenseLocalizationId,
                w.id AS wordId,
                w.word AS word,
                w.pos AS pos,
                w.cert_level AS certLevel,
                we.text AS sentence,
                NULL AS trans
            FROM words w
            JOIN word_senses ws
                ON w.id = ws.word_id
            JOIN word_examples we
                ON w.id = we.word_id
                AND ws.id = we.sense_id
            WHERE w.id = :wordId
            """, nativeQuery = true)
    List<WordExampleProjection> findWordExamples(@Param("wordId") String wordId);

    @Query(value = """
            SELECT
                we.id AS wordExampleId,
                ws.id AS senseId,
                NULL AS wordSenseLocalizationId,
                w.id AS wordId,
                w.word AS word,
                w.pos AS pos,
                w.cert_level AS certLevel,
                we.text AS sentence,
                wel.translated_text AS trans
            FROM words w
            JOIN word_senses ws
                ON w.id = ws.word_id
            JOIN word_examples we
                ON w.id = we.word_id
                AND ws.id = we.sense_id
            LEFT JOIN word_example_localizations wel
                ON we.id = wel.example_id
                AND ws.id = wel.sense_id
                AND wel.lang_code = :transLangCode
            WHERE w.id = :wordId
            """, nativeQuery = true)
    List<WordExampleProjection> findWordExamplesWithTrans(
            @Param("wordId") String wordId,
            @Param("transLangCode") String transLangCode
    );

    @Query(value = """
            SELECT
                we.id AS wordExampleId,
                wsl.sense_id AS senseId,
                wsl.id AS wordSenseLocalizationId,
                w.id AS wordId,
                w.word AS word,
                w.pos AS pos,
                w.cert_level AS certLevel,
                we.text AS sentence,
                NULL AS trans
            FROM words w
            JOIN word_sense_localizations wsl
                ON w.id = wsl.word_id
            JOIN word_examples we
                ON w.id = we.word_id
                AND wsl.id = we.sense_id
            WHERE w.id = :wordId
                AND wsl.sense_id IS NULL
                AND wsl.source = 'MOCHI'
            """, nativeQuery = true)
    List<WordExampleProjection> findMochiWordExamplesWithoutTrans(@Param("wordId") String wordId);

    @Query(value = """
            SELECT
                we.id AS wordExampleId,
                wsl.sense_id AS senseId,
                wsl.id AS wordSenseLocalizationId,
                w.id AS wordId,
                w.word AS word,
                w.pos AS pos,
                w.cert_level AS certLevel,
                we.text AS sentence,
                wel.translated_text AS trans
            FROM words w
            JOIN word_sense_localizations wsl
                ON w.id = wsl.word_id
            JOIN word_examples we
                ON w.id = we.word_id
                AND wsl.id = we.sense_id
            LEFT JOIN word_example_localizations wel
                ON we.id = wel.example_id
                AND wsl.id = wel.sense_id
                AND (:transLangCode IS NULL OR wel.lang_code = :transLangCode)
            WHERE w.id = :wordId
                AND wsl.sense_id IS NULL
                AND wsl.source = 'MOCHI'
                AND (:transLangCode IS NULL OR wsl.lang_code = :transLangCode)
            """, nativeQuery = true)
    List<WordExampleProjection> findMochiWordExamples(
            @Param("wordId") String wordId,
            @Param("transLangCode") String transLangCode
    );
}
