package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.UserVocabulary;
import com.bachdauduc.vocab_app.repository.projection.UserVocabularyProjection;
import com.bachdauduc.vocab_app.repository.projection.UserVocabularyExportProjection;
import com.bachdauduc.vocab_app.repository.projection.UserVocabularyAutocompleteProjection;
import com.bachdauduc.vocab_app.repository.projection.UserVocabularyLevelQuantityProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserVocabularyRepository extends JpaRepository<UserVocabulary, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT uv FROM UserVocabulary uv WHERE uv.id = :id")
    Optional<UserVocabulary> findByIdForUpdate(@Param("id") String id);

    Page<UserVocabulary> findByUserIdAndLevel(String userId, Integer level, Pageable pageable);

    @Query(value = """
            SELECT w.word AS word, w.pos AS pos, uv.level AS level,
                COALESCE(
                    CASE WHEN saved.lang_code = :langCode THEN NULLIF(TRIM(saved.short_meaning), '') END,
                    CASE WHEN saved.lang_code = :langCode
                         THEN NULLIF(TRIM(saved.full_localized_definition), '') END,
                    (SELECT COALESCE(NULLIF(TRIM(trans.short_meaning), ''),
                                     NULLIF(TRIM(trans.full_localized_definition), ''))
                     FROM word_sense_localizations trans
                     WHERE trans.sense_id = ws.id AND trans.word_id = w.id
                         AND trans.lang_code = :langCode
                         AND (NULLIF(TRIM(trans.short_meaning), '') IS NOT NULL
                              OR NULLIF(TRIM(trans.full_localized_definition), '') IS NOT NULL)
                     ORDER BY trans.id ASC
                     LIMIT 1),
                    ws.definition, ''
                ) AS wordSense
            FROM user_vocabularies uv
            JOIN words w ON w.id = uv.word_id
            LEFT JOIN word_sense_localizations saved
                ON saved.id = uv.sense_localized_id AND saved.word_id = uv.word_id
            LEFT JOIN word_senses ws
                ON ws.id = COALESCE(uv.sense_id, saved.sense_id) AND ws.word_id = uv.word_id
            WHERE uv.user_id = :userId
            ORDER BY w.normalized_word ASC, w.word ASC, uv.id ASC
            """, nativeQuery = true)
    Slice<UserVocabularyExportProjection> findUserVocabularyForExport(
            @Param("userId") String userId,
            @Param("langCode") String langCode,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT
                        uv.id AS id,
                        uv.user_id AS userId,
                        uv.word_id AS wordId,
                        w.word AS word,
                        uv.sense_id AS senseId,
                        uv.sense_localized_id AS senseLocalizedId,
                        uv.level AS level,
                        uv.current_level_correct_turns AS currentLevelCorrectTurns,
                        uv.next_review_at AS nextReviewAt,
                        uv.created_at AS createdAt,
                        uv.updated_at AS updatedAt
                    FROM user_vocabularies uv
                    JOIN words w ON uv.word_id = w.id
                    WHERE uv.user_id = :userId
                        AND uv.level = :level
                    ORDER BY uv.next_review_at ASC, uv.created_at DESC, uv.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM user_vocabularies uv
                    WHERE uv.user_id = :userId
                        AND uv.level = :level
                    """,
            nativeQuery = true
    )
    Page<UserVocabularyProjection> findUserVocabByLevelWithWord(
            @Param("userId") String userId,
            @Param("level") Integer level,
            Pageable pageable
    );



    @Query(
            value = """
                    SELECT
                        uv.id AS id,
                        uv.user_id AS userId,
                        uv.word_id AS wordId,
                        w.word AS word,
                        uv.sense_id AS senseId,
                        uv.sense_localized_id AS senseLocalizedId,
                        uv.level AS level,
                        uv.current_level_correct_turns AS currentLevelCorrectTurns,
                        uv.next_review_at AS nextReviewAt,
                        uv.created_at AS createdAt,
                        uv.updated_at AS updatedAt
                    FROM user_vocabularies uv
                    JOIN words w ON uv.word_id = w.id
                    WHERE uv.user_id = :userId
                        AND w.normalized_word = :normalizedText
                    ORDER BY w.normalized_word ASC, w.word ASC, uv.created_at DESC, uv.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM user_vocabularies uv
                    JOIN words w ON uv.word_id = w.id
                    WHERE uv.user_id = :userId
                        AND w.normalized_word = :normalizedText
                    """,
            nativeQuery = true
    )
    Page<UserVocabularyProjection> findUserVocabByNormalizedWord(
            @Param("userId") String userId,
            @Param("normalizedText") String normalizedText,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT
                        uv.id AS userVocabId,
                        w.word AS word,
                        uv.level AS level,
                        w.pos AS pos
                    FROM user_vocabularies uv
                    JOIN words w ON uv.word_id = w.id
                    WHERE uv.user_id = :userId
                        AND w.normalized_word LIKE CONCAT(:normalizedPrefix, '%')
                    ORDER BY w.normalized_word ASC, w.word ASC, uv.created_at DESC, uv.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM user_vocabularies uv
                    JOIN words w ON uv.word_id = w.id
                    WHERE uv.user_id = :userId
                        AND w.normalized_word LIKE CONCAT(:normalizedPrefix, '%')
                    """,
            nativeQuery = true
    )
    Page<UserVocabularyAutocompleteProjection> findUserVocabByNormalizedWordPrefix(
            @Param("userId") String userId,
            @Param("normalizedPrefix") String normalizedPrefix,
            Pageable pageable
    );

    boolean existsByUserIdAndWordIdAndSenseId(String userId, String wordId, String senseId);

    boolean existsByUserIdAndWordIdAndSenseLocalizedId(String userId, String wordId, String senseLocalizedId);

    @Query("""
            SELECT uv.level AS level, COUNT(uv) AS quantity
            FROM UserVocabulary uv
            WHERE uv.userId = :userId
            GROUP BY uv.level
            ORDER BY uv.level
            """)
    List<UserVocabularyLevelQuantityProjection> countUserVocabularyByLevel(
            @Param("userId") String userId
    );

    @Query("""
            SELECT COUNT(uv)
            FROM UserVocabulary uv
            WHERE uv.userId = :userId
                AND uv.nextReviewAt IS NOT NULL
                AND uv.nextReviewAt <= :now
            """)
    long countDueReviewVocabs(
            @Param("userId") String userId,
            @Param("now") LocalDateTime now
    );

    @Query(value = """
            SELECT uv
            FROM UserVocabulary uv
            WHERE uv.userId = :userId
                AND uv.nextReviewAt <= :now
            ORDER BY uv.level ASC, uv.nextReviewAt ASC
            """)
    List<UserVocabulary> findDueReviewVocabs(
            @Param("userId") String userId,
            @Param("now") LocalDateTime now
    );

    @Query(value = """
            SELECT uv
            FROM UserVocabulary uv
            WHERE uv.userId = :userId
                AND uv.nextReviewAt <= :now
            ORDER BY uv.level ASC, uv.nextReviewAt ASC
            """)
    List<UserVocabulary> findDueReviewVocabs(
            @Param("userId") String userId,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}
