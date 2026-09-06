package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.ListenAndTypeExerciseChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ListenAndTypeExerciseChallengeRepository extends JpaRepository<ListenAndTypeExerciseChallenge, String> {
    List<ListenAndTypeExerciseChallenge> findByListenExerciseIdOrderByPositionAsc(String listenExerciseId);

    long countByListenExerciseId(String listenExerciseId);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
            UPDATE ListenAndTypeExerciseChallenge c SET c.translate = :translation
            WHERE c.id = :id
                AND (c.content = :content OR (c.content IS NULL AND :content IS NULL))
                AND (c.translate IS NULL OR TRIM(c.translate) = '')
            """)
    int saveTranslationIfMissing(
            @Param("id") String id,
            @Param("content") String content,
            @Param("translation") String translation
    );
}
