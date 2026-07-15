package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.WordSense;
import com.bachdauduc.vocab_app.repository.projection.WordSenseProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WordSenseRepository extends JpaRepository<WordSense, String> {
    List<WordSense> findByWordId(String wordId);

    boolean existsByIdAndWordId(String id, String wordId);

    @Query(value = """
            SELECT
                ws.id AS senseId,
                NULL AS localizationId,
                w.id AS wordId,
                w.word AS word,
                w.pos AS pos,
                w.cert_level AS certLevel,
                NULL AS shortMeaning,
                ws.definition AS definition,
                ws.synonyms AS synonyms,
                ws.antonyms AS antonyms,
                NULL AS transLangCode,
                NULL AS transShortMeaning,
                NULL AS transDefinition,
                ws.derived AS derived,
                ws.coordinate_terms AS coordinateTerms,
                ws.form_of AS formOf,
                ws.alt_of AS altOf
            FROM words w
            JOIN word_senses ws ON w.id = ws.word_id
            WHERE w.id = :wordId
            """, nativeQuery = true)
    List<WordSenseProjection> findWordSenses(@Param("wordId") String wordId);

    @Query(value = """
            SELECT
                ws.id AS senseId,
                wsl.id AS localizationId,
                w.id AS wordId,
                w.word AS word,
                w.pos AS pos,
                w.cert_level AS certLevel,
                wsl.short_meaning AS shortMeaning,
                ws.definition AS definition,
                ws.synonyms AS synonyms,
                ws.antonyms AS antonyms,
                wsl.lang_code AS transLangCode,
                wsl.short_meaning AS transShortMeaning,
                wsl.full_localized_definition AS transDefinition,
                ws.derived AS derived,
                ws.coordinate_terms AS coordinateTerms,
                ws.form_of AS formOf,
                ws.alt_of AS altOf
            FROM words w
            JOIN word_senses ws ON w.id = ws.word_id
            LEFT JOIN word_sense_localizations wsl
                ON ws.id = wsl.sense_id
                AND wsl.lang_code = :transLangCode
            WHERE w.id = :wordId
            """, nativeQuery = true)
    List<WordSenseProjection> findWordSensesWithTrans(
            @Param("wordId") String wordId,
            @Param("transLangCode") String transLangCode
    );

    @Query(value = """
            SELECT
                wsl.sense_id AS senseId,
                wsl.id AS localizationId,
                w.id AS wordId,
                w.word AS word,
                w.pos AS pos,
                w.cert_level AS certLevel,
                wsl.short_meaning AS shortMeaning,
                NULL AS definition,
                NULL AS synonyms,
                NULL AS antonyms,
                wsl.lang_code AS transLangCode,
                wsl.short_meaning AS transShortMeaning,
                wsl.full_localized_definition AS transDefinition,
                NULL AS derived,
                NULL AS coordinateTerms,
                NULL AS formOf,
                NULL AS altOf
            FROM words w
            JOIN word_sense_localizations wsl ON w.id = wsl.word_id
            WHERE w.id = :wordId
                AND wsl.sense_id IS NULL
                AND wsl.source = 'MOCHI'
                AND (:transLangCode IS NULL OR wsl.lang_code = :transLangCode)
            """, nativeQuery = true)
    List<WordSenseProjection> findMochiWordSenseLocalizations(
            @Param("wordId") String wordId,
            @Param("transLangCode") String transLangCode
    );
}
