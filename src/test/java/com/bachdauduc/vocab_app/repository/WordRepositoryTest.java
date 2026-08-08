package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.Word;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class WordRepositoryTest {
    @Autowired
    WordRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpCaseInsensitiveWordColumn() {
        jdbcTemplate.execute("""
                CREATE ALIAS IF NOT EXISTS BINARY AS
                'byte[] binary(String value) {
                    return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }'
                """);
        jdbcTemplate.execute("ALTER TABLE words ALTER COLUMN word VARCHAR_IGNORECASE NOT NULL");
        jdbcTemplate.update("DELETE FROM words");
        insertWord("1", "Apple");
        insertWord("2", "Apple");
        insertWord("3", "apple");
        insertWord("4", "apple");
    }

    @Test
    void exactSearchGroupsByCaseSensitiveWordTuple() {
        assertThat(repository.findByNormalizedWord("apple"))
                .extracting(Word::getWord)
                .containsExactlyInAnyOrder("Apple", "apple");
    }

    @Test
    void prefixSearchGroupsByCaseSensitiveWordTuple() {
        assertThat(repository.findByNormalizedWordPrefix("app"))
                .extracting(Word::getWord)
                .containsExactlyInAnyOrder("Apple", "apple");
    }

    @Test
    void uniqueWordPrefixKeepsDifferentLetterCase() {
        assertThat(repository.findUniqueWordsByNormalizedWordPrefix("app"))
                .containsExactlyInAnyOrder("Apple", "apple");
    }

    private void insertWord(String id, String text) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.of(2026, 8, 6, 0, 0));
        jdbcTemplate.update(
                """
                        INSERT INTO words (
                            id, word, normalized_word, pos, lang, lang_code,
                            word_source, cert_level, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                text,
                "apple",
                "noun",
                "English",
                "en",
                "LOCAL",
                "B1",
                now,
                now
        );
    }
}
