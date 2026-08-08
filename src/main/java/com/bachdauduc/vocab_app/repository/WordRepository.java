package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.Word;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WordRepository extends JpaRepository<Word, String> {
    List<Word> findByWord(String word);

    @Query(value = """
            SELECT w.*
            FROM words w
            JOIN (
                SELECT MIN(w2.id) AS id
                FROM words w2
                WHERE w2.normalized_word = :normalizedWord
                GROUP BY BINARY(w2.word), w2.pos, w2.word_source, w2.cert_level
            ) representative ON representative.id = w.id
            ORDER BY w.normalized_word ASC, w.word ASC, w.id ASC
            """, nativeQuery = true)
    List<Word> findByNormalizedWord(@Param("normalizedWord") String normalizedWord);

    @Query(value = """
            SELECT w.*
            FROM words w
            JOIN (
                SELECT MIN(w2.id) AS id
                FROM words w2
                WHERE w2.normalized_word LIKE CONCAT(:normalizedPrefix, '%')
                GROUP BY BINARY(w2.word), w2.pos, w2.word_source, w2.cert_level
            ) representative ON representative.id = w.id
            ORDER BY w.normalized_word ASC, w.word ASC, w.id ASC
            """, nativeQuery = true)
    List<Word> findByNormalizedWordPrefix(@Param("normalizedPrefix") String normalizedPrefix);

    @Query(value = """
            SELECT w.word
            FROM words w
            JOIN (
                SELECT MIN(w2.id) AS id
                FROM words w2
                WHERE w2.normalized_word LIKE CONCAT(:normalizedPrefix, '%')
                GROUP BY BINARY(w2.word)
            ) representative ON representative.id = w.id
            ORDER BY w.word ASC, w.id ASC
            """, nativeQuery = true)
    List<String> findUniqueWordsByNormalizedWordPrefix(@Param("normalizedPrefix") String normalizedPrefix);

    @Query(
            value = """
                    SELECT DISTINCT w.*
                    FROM words w
                    JOIN word_category wc ON w.id = wc.word_id
                    WHERE wc.category_id = :categoryId
                    ORDER BY w.word ASC, w.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT w.id)
                    FROM words w
                    JOIN word_category wc ON w.id = wc.word_id
                    WHERE wc.category_id = :categoryId
                    """,
            nativeQuery = true
    )
    Page<Word> findWordsByCategoryId(
            @Param("categoryId") String categoryId,
            Pageable pageable
    );

    Page<Word> findByCertLevel(String certLevel, Pageable pageable);

    @Query(
            value = """
                    SELECT DISTINCT w.*
                    FROM words w
                    JOIN word_category wc ON w.id = wc.word_id
                    WHERE wc.category_id = :categoryId
                        AND w.normalized_word = :normalizedText
                    ORDER BY w.word ASC, w.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT w.id)
                    FROM words w
                    JOIN word_category wc ON w.id = wc.word_id
                    WHERE wc.category_id = :categoryId
                        AND w.normalized_word = :normalizedText
                    """,
            nativeQuery = true
    )
    Page<Word> findWordsByCategoryIdAndNormalizedWord(
            @Param("categoryId") String categoryId,
            @Param("normalizedText") String normalizedText,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT DISTINCT w.*
                    FROM words w
                    JOIN word_category wc ON w.id = wc.word_id
                    WHERE wc.category_id = :categoryId
                        AND w.normalized_word LIKE CONCAT(:normalizedPrefix, '%')
                    ORDER BY w.normalized_word ASC, w.word ASC, w.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT w.id)
                    FROM words w
                    JOIN word_category wc ON w.id = wc.word_id
                    WHERE wc.category_id = :categoryId
                        AND w.normalized_word LIKE CONCAT(:normalizedPrefix, '%')
                    """,
            nativeQuery = true
    )
    Page<Word> findWordsByCategoryIdAndNormalizedWordPrefix(
            @Param("categoryId") String categoryId,
            @Param("normalizedPrefix") String normalizedPrefix,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT *
                    FROM words w
                    WHERE UPPER(w.cert_level) = :certLevel
                        AND w.normalized_word = :normalizedText
                    ORDER BY w.word ASC, w.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM words w
                    WHERE UPPER(w.cert_level) = :certLevel
                        AND w.normalized_word = :normalizedText
                    """,
            nativeQuery = true
    )
    Page<Word> findWordsByCertLevelAndNormalizedWord(
            @Param("certLevel") String certLevel,
            @Param("normalizedText") String normalizedText,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT *
                    FROM words w
                    WHERE UPPER(w.cert_level) = :certLevel
                        AND w.normalized_word LIKE CONCAT(:normalizedPrefix, '%')
                    ORDER BY w.normalized_word ASC, w.word ASC, w.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM words w
                    WHERE UPPER(w.cert_level) = :certLevel
                        AND w.normalized_word LIKE CONCAT(:normalizedPrefix, '%')
                    """,
            nativeQuery = true
    )
    Page<Word> findWordsByCertLevelAndNormalizedWordPrefix(
            @Param("certLevel") String certLevel,
            @Param("normalizedPrefix") String normalizedPrefix,
            Pageable pageable
    );
}
