package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.repository.projection.UserVocabularyLevelQuantityProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

@DataJpaTest
class UserVocabularyInfoRepositoryTest {
    @Autowired
    UserVocabularyRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void aggregatesQuantityByLevelForOnlyTheRequestedUser() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 8, 12, 0);
        insertVocabulary("user-1", 1, now.minusHours(1));
        insertVocabulary("user-1", 1, now.plusHours(1));
        insertVocabulary("user-1", 3, null);
        insertVocabulary("user-2", 1, now.minusHours(1));

        assertThat(repository.countUserVocabularyByLevel("user-1"))
                .extracting(
                        UserVocabularyLevelQuantityProjection::getLevel,
                        UserVocabularyLevelQuantityProjection::getQuantity
                )
                .containsExactlyInAnyOrder(
                        tuple(1, 2L),
                        tuple(3, 1L)
                );
    }

    @Test
    void countsOnlyVocabularyDueAtOrBeforeNow() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 8, 12, 0);
        insertVocabulary("user-1", 1, now.minusMinutes(1));
        insertVocabulary("user-1", 2, now);
        insertVocabulary("user-1", 3, now.plusMinutes(1));
        insertVocabulary("user-1", 4, null);
        insertVocabulary("user-2", 1, now.minusMinutes(1));

        assertThat(repository.countDueReviewVocabs("user-1", now)).isEqualTo(2L);
    }

    private void insertVocabulary(String userId, int level, LocalDateTime nextReviewAt) {
        LocalDateTime timestamp = LocalDateTime.of(2026, 8, 8, 8, 0);
        jdbcTemplate.update(
                """
                        INSERT INTO user_vocabularies (
                            id, user_id, word_id, sense_id, sense_localized_id,
                            level, current_level_correct_turns, next_review_at,
                            created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID().toString(),
                userId,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                null,
                level,
                0,
                nextReviewAt == null ? null : Timestamp.valueOf(nextReviewAt),
                Timestamp.valueOf(timestamp),
                Timestamp.valueOf(timestamp)
        );
    }
}
