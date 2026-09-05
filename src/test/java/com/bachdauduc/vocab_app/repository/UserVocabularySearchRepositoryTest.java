package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.repository.projection.UserVocabularyProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

@DataJpaTest
class UserVocabularySearchRepositoryTest {
    @Autowired
    UserVocabularyRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM user_vocabularies");
        jdbcTemplate.update("DELETE FROM words");

        insertWord("word-apple", "Apple", "apple");
        insertWord("word-application", "Application", "application");
        insertWord("word-banana", "Banana", "banana");

        insertVocabulary("saved-new", "user-1", "word-apple", "sense-2",
                LocalDateTime.of(2026, 9, 5, 10, 0));
        insertVocabulary("saved-old", "user-1", "word-apple", "sense-1",
                LocalDateTime.of(2026, 9, 4, 10, 0));
        insertVocabulary("saved-application", "user-1", "word-application", "sense-3",
                LocalDateTime.of(2026, 9, 3, 10, 0));
        insertVocabulary("saved-banana", "user-1", "word-banana", "sense-4",
                LocalDateTime.of(2026, 9, 2, 10, 0));
        insertVocabulary("other-user-apple", "user-2", "word-apple", "sense-5",
                LocalDateTime.of(2026, 9, 5, 11, 0));
    }

    @Test
    void exactSearchReturnsEveryMatchingSavedSenseForRequestedUser() {
        Page<UserVocabularyProjection> result = repository.findUserVocabByNormalizedWord(
                "user-1",
                "apple",
                PageRequest.of(0, 20)
        );

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(
                        UserVocabularyProjection::getId,
                        UserVocabularyProjection::getWord,
                        UserVocabularyProjection::getSenseId
                )
                .containsExactly(
                        tuple("saved-new", "Apple", "sense-2"),
                        tuple("saved-old", "Apple", "sense-1")
                );
    }

    @Test
    void prefixSearchIsUserScopedAndKeepsPaginationMetadata() {
        Page<UserVocabularyProjection> firstPage = repository.findUserVocabByNormalizedWordPrefix(
                "user-1",
                "app",
                PageRequest.of(0, 2)
        );
        Page<UserVocabularyProjection> secondPage = repository.findUserVocabByNormalizedWordPrefix(
                "user-1",
                "app",
                PageRequest.of(1, 2)
        );

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent())
                .extracting(UserVocabularyProjection::getId)
                .containsExactly("saved-new", "saved-old");
        assertThat(secondPage.getContent())
                .extracting(UserVocabularyProjection::getId)
                .containsExactly("saved-application");
    }

    private void insertWord(String id, String word, String normalizedWord) {
        Timestamp timestamp = Timestamp.valueOf(LocalDateTime.of(2026, 9, 1, 8, 0));
        jdbcTemplate.update(
                """
                        INSERT INTO words (
                            id, word, normalized_word, pos, lang, lang_code,
                            word_source, cert_level, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                word,
                normalizedWord,
                "noun",
                "English",
                "en",
                "LOCAL",
                "B1",
                timestamp,
                timestamp
        );
    }

    private void insertVocabulary(
            String id,
            String userId,
            String wordId,
            String senseId,
            LocalDateTime createdAt
    ) {
        Timestamp timestamp = Timestamp.valueOf(createdAt);
        jdbcTemplate.update(
                """
                        INSERT INTO user_vocabularies (
                            id, user_id, word_id, sense_id, sense_localized_id,
                            level, current_level_correct_turns, next_review_at,
                            created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                userId,
                wordId,
                senseId,
                null,
                1,
                0,
                timestamp,
                timestamp,
                timestamp
        );
    }
}
