package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.UserVocabulary;
import com.bachdauduc.vocab_app.repository.projection.UserVocabularyProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UserVocabularyRepository extends JpaRepository<UserVocabulary, String> {
    Page<UserVocabulary> findByUserIdAndLevel(String userId, Integer level, Pageable pageable);

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

    boolean existsByUserIdAndWordIdAndSenseId(String userId, String wordId, String senseId);

    boolean existsByUserIdAndWordIdAndSenseLocalizedId(String userId, String wordId, String senseLocalizedId);

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
