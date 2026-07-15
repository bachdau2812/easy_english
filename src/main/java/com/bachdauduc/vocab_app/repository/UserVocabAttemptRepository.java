package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.UserVocabAttempt;
import com.bachdauduc.vocab_app.repository.projection.UserVocabAttemptProjection;
import com.bachdauduc.vocab_app.repository.projection.UserVocabStatisticProjection;
import com.bachdauduc.vocab_app.repository.projection.WrongVocabProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UserVocabAttemptRepository extends JpaRepository<UserVocabAttempt, String> {
    List<UserVocabAttempt> findByUserIdAndAttemptIdIn(String userId, List<String> attemptIds);

    @Query(value = """
            SELECT DISTINCT c.id
            FROM listen_and_type_exercise_challenges c
            JOIN user_vocab_attempts a
                ON c.id = a.attempt_id
                AND a.user_id = :userId
                AND a.exercise_type = 'LAT_LISTEN_AND_TYPE'
            WHERE c.listen_exercise_id = :lessonId
            """, nativeQuery = true)
    List<String> findCompletedListenAndTypeChallengeIds(
            @Param("userId") String userId,
            @Param("lessonId") String lessonId
    );

    @Query(value = """
            SELECT COUNT(DISTINCT c.id)
            FROM listen_and_type_exercise_challenges c
            JOIN user_vocab_attempts a
                ON c.id = a.attempt_id
                AND a.user_id = :userId
                AND a.exercise_type = 'LAT_LISTEN_AND_TYPE'
            WHERE c.listen_exercise_id = :lessonId
            """, nativeQuery = true)
    long countCompletedListenAndTypeChallenges(
            @Param("userId") String userId,
            @Param("lessonId") String lessonId
    );

    @Query(
            value = """
                    SELECT
                        a.id AS id,
                        a.attempt_id AS attemptId,
                        a.user_id AS userId,
                        a.user_vocab_id AS userVocabId,
                        a.exercise_type AS exerciseType,
                        a.user_answer AS userAnswer,
                        a.is_correct AS correct,
                        a.replay_count AS replayCount,
                        a.created_at AS createdAt
                    FROM user_vocab_attempts a
                    WHERE a.user_id = :userId
                        AND a.created_at >= :fromTime
                        AND a.created_at < :toTime
                        AND (:exercisePrefix IS NULL OR a.exercise_type LIKE CONCAT(:exercisePrefix, '%'))
                    ORDER BY a.created_at DESC, a.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM user_vocab_attempts a
                    WHERE a.user_id = :userId
                        AND a.created_at >= :fromTime
                        AND a.created_at < :toTime
                        AND (:exercisePrefix IS NULL OR a.exercise_type LIKE CONCAT(:exercisePrefix, '%'))
                    """,
            nativeQuery = true
    )
    Page<UserVocabAttemptProjection> findUserAttempts(
            @Param("userId") String userId,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime,
            @Param("exercisePrefix") String exercisePrefix,
            Pageable pageable
    );

    @Query(value = """
            SELECT
                (SELECT COUNT(*)
                 FROM user_vocab_attempts a
                 WHERE a.user_id = :userId
                    AND a.created_at >= :fromTime
                    AND a.created_at < :toTime) AS totalAttempts,
                (SELECT COALESCE(SUM(CASE WHEN (a.exercise_type LIKE 'QUIZ_%' OR a.exercise_type LIKE 'LAT_%') AND a.is_correct = TRUE THEN 1 ELSE 0 END), 0)
                 FROM user_vocab_attempts a
                 WHERE a.user_id = :userId
                    AND a.created_at >= :fromTime
                    AND a.created_at < :toTime) AS correctQuizAttempt,
                (SELECT COALESCE(SUM(CASE WHEN (a.exercise_type LIKE 'QUIZ_%' OR a.exercise_type LIKE 'LAT_%') AND a.is_correct = FALSE THEN 1 ELSE 0 END), 0)
                 FROM user_vocab_attempts a
                 WHERE a.user_id = :userId
                    AND a.created_at >= :fromTime
                    AND a.created_at < :toTime) AS wrongQuizAttempt,
                (SELECT COUNT(*)
                 FROM (
                    SELECT a.user_vocab_id
                    FROM user_vocab_attempts a
                    WHERE a.user_id = :userId
                        AND a.created_at >= :fromTime
                        AND a.created_at < :toTime
                        AND a.exercise_type LIKE 'VOCAB_%'
                        AND a.user_vocab_id IS NOT NULL
                    GROUP BY a.user_vocab_id
                 ) total_vocab) AS totalUniqueVocab,
                (SELECT COUNT(*)
                 FROM (
                    SELECT
                        a.user_vocab_id,
                        MAX(CASE WHEN a.is_correct = FALSE THEN 1 ELSE 0 END) AS hasWrong,
                        MAX(CASE WHEN a.is_correct = TRUE THEN 1 ELSE 0 END) AS hasCorrect
                    FROM user_vocab_attempts a
                    WHERE a.user_id = :userId
                        AND a.created_at >= :fromTime
                        AND a.created_at < :toTime
                        AND a.exercise_type LIKE 'VOCAB_%'
                        AND a.user_vocab_id IS NOT NULL
                    GROUP BY a.user_vocab_id
                 ) vocab_result
                 WHERE vocab_result.hasWrong = 0
                    AND vocab_result.hasCorrect = 1) AS correctUniqueVocab,
                (SELECT COUNT(*)
                 FROM (
                    SELECT
                        a.user_vocab_id,
                        MAX(CASE WHEN a.is_correct = FALSE THEN 1 ELSE 0 END) AS hasWrong
                    FROM user_vocab_attempts a
                    WHERE a.user_id = :userId
                        AND a.created_at >= :fromTime
                        AND a.created_at < :toTime
                        AND a.exercise_type LIKE 'VOCAB_%'
                        AND a.user_vocab_id IS NOT NULL
                    GROUP BY a.user_vocab_id
                 ) vocab_result
                 WHERE vocab_result.hasWrong = 1) AS wrongUniqueVocab,
                CAST(NULL AS SIGNED) AS wrongCountVocab
            """, nativeQuery = true)
    UserVocabStatisticProjection getUserDailyStatistic(
            @Param("userId") String userId,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime
    );

    @Query(value = """
            SELECT
                COUNT(*) AS totalAttempts,
                COALESCE(SUM(CASE WHEN (a.exercise_type LIKE 'QUIZ_%' OR a.exercise_type LIKE 'LAT_%') AND a.is_correct = TRUE THEN 1 ELSE 0 END), 0) AS correctQuizAttempt,
                COALESCE(SUM(CASE WHEN (a.exercise_type LIKE 'QUIZ_%' OR a.exercise_type LIKE 'LAT_%') AND a.is_correct = FALSE THEN 1 ELSE 0 END), 0) AS wrongQuizAttempt,
                COUNT(DISTINCT CASE WHEN a.exercise_type LIKE 'VOCAB_%' AND a.user_vocab_id IS NOT NULL THEN a.user_vocab_id END) AS totalUniqueVocab,
                CAST(NULL AS SIGNED) AS correctUniqueVocab,
                CAST(NULL AS SIGNED) AS wrongUniqueVocab,
                COALESCE(SUM(CASE WHEN a.exercise_type LIKE 'VOCAB_%' AND a.is_correct = FALSE THEN 1 ELSE 0 END), 0) AS wrongCountVocab
            FROM user_vocab_attempts a
            WHERE a.user_id = :userId
            """, nativeQuery = true)
    UserVocabStatisticProjection getUserOverallStatistic(@Param("userId") String userId);

    @Query(value = """
            SELECT
                a.user_vocab_id AS userVocabId,
                w.word AS word,
                COUNT(*) AS wrongCount
            FROM user_vocab_attempts a
            JOIN user_vocabularies uv ON a.user_vocab_id = uv.id
            JOIN words w ON uv.word_id = w.id
            WHERE a.user_id = :userId
                AND a.exercise_type LIKE 'VOCAB_%'
                AND a.is_correct = FALSE
                AND (:fromTime IS NULL OR a.created_at >= :fromTime)
                AND (:toTime IS NULL OR a.created_at < :toTime)
            GROUP BY a.user_vocab_id, w.word
            ORDER BY wrongCount DESC, w.word ASC
            """, nativeQuery = true)
    List<WrongVocabProjection> findWrongVocabs(
            @Param("userId") String userId,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime
    );

    @Query(value = """
            SELECT
                a.user_vocab_id AS userVocabId,
                w.word AS word,
                COUNT(*) AS wrongCount
            FROM user_vocab_attempts a
            JOIN user_vocabularies uv ON a.user_vocab_id = uv.id
            JOIN words w ON uv.word_id = w.id
            WHERE a.user_id = :userId
                AND a.exercise_type LIKE 'VOCAB_%'
                AND a.is_correct = FALSE
            GROUP BY a.user_vocab_id, w.word
            HAVING COUNT(*) > :minWrongCount
            ORDER BY wrongCount DESC, w.word ASC
            """, nativeQuery = true)
    List<WrongVocabProjection> findMostWrongVocabs(
            @Param("userId") String userId,
            @Param("minWrongCount") long minWrongCount
    );
}
