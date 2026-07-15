package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.UserSearchHistory;
import com.bachdauduc.vocab_app.repository.projection.UserSearchHistoryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSearchHistoryRepository extends JpaRepository<UserSearchHistory, String> {
    boolean existsByUserIdAndWordId(String userId, String wordId);

    @Modifying
    @Query(value = """
            UPDATE user_search_history
            SET searched_at = CURRENT_TIMESTAMP
            WHERE user_id = :userId
                AND word_id = :wordId
            """, nativeQuery = true)
    int refreshSearchHistory(
            @Param("userId") String userId,
            @Param("wordId") String wordId
    );

    @Query(
            value = """
                    SELECT
                        ush.id AS id,
                        ush.user_id AS userId,
                        ush.word_id AS wordId,
                        w.word AS word,
                        ush.searched_at AS searchedAt
                    FROM user_search_history ush
                    JOIN words w ON ush.word_id = w.id
                    WHERE ush.user_id = :userId
                    ORDER BY ush.searched_at DESC, ush.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM user_search_history ush
                    WHERE ush.user_id = :userId
                    """,
            nativeQuery = true
    )
    Page<UserSearchHistoryProjection> findUserSearchHistory(
            @Param("userId") String userId,
            Pageable pageable
    );
}
