package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.repository.projection.UserVocabularyExportProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserVocabularyExportRepositoryTest {
    @Autowired UserVocabularyRepository repository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM user_vocabularies");
        jdbc.update("DELETE FROM word_sense_localizations");
        jdbc.update("DELETE FROM word_senses");
        jdbc.update("DELETE FROM words");
        jdbc.update("""
                INSERT INTO words (id, word, normalized_word, pos, lang, lang_code,
                    word_source, created_at, updated_at)
                VALUES ('word-1', 'Apple', 'apple', 'noun', 'English', 'en', 'LOCAL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        sense("sense-1", "An apple");
        sense("sense-2", "A company");
        sense("sense-3", "A full definition");
        sense("sense-4", "English fallback");
        localization("loc-1", "sense-1", "vi", "quả táo", "Một loại quả có vỏ đỏ hoặc xanh.");
        localization("loc-2", "sense-1", "vi", "bản dịch thứ hai", "Định nghĩa đầy đủ thứ hai.");
        localization("loc-fr", "sense-1", "fr", "pomme", "Le fruit du pommier.");
        localization("loc-saved", null, "vi", "nghĩa riêng", "Định nghĩa riêng đầy đủ.");
        localization("loc-full", "sense-3", "vi", "   ", "định nghĩa đầy đủ");
        localization("loc-empty", "sense-4", "vi", "Chỉ có nghĩa ngắn", "   ");
        vocabulary("saved-1", "user-1", "sense-1", null, 1);
        vocabulary("saved-2", "user-1", "sense-2", null, 2);
        vocabulary("saved-3", "user-1", null, "loc-saved", 3);
        vocabulary("saved-4", "user-1", "sense-3", null, 4);
        vocabulary("saved-5", "user-1", "sense-4", null, 5);
        vocabulary("saved-6", "user-1", null, "loc-fr", 6);
        vocabulary("other-user", "user-2", "sense-1", null, 1);
    }

    @Test
    void resolvesVietnameseThenEnglishWithoutDuplicatingRowsOrLeakingOtherUsers() {
        var result = repository.findUserVocabularyForExport("user-1", "vi", PageRequest.of(0, 500));
        assertThat(result.getContent()).extracting(UserVocabularyExportProjection::getWordSense)
                .containsExactly("Một loại quả có vỏ đỏ hoặc xanh.", "A company", "Định nghĩa riêng đầy đủ.",
                        "định nghĩa đầy đủ", "English fallback", "Một loại quả có vỏ đỏ hoặc xanh.");
        assertThat(result.getContent()).extracting(UserVocabularyExportProjection::getLevel)
                .containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(result.getContent()).allSatisfy(row -> {
            assertThat(row.getWord()).isEqualTo("Apple");
            assertThat(row.getPos()).isEqualTo("noun");
        });
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void usesRequestedLanguageAndCanReadEveryBatch() {
        var first = repository.findUserVocabularyForExport("user-1", "fr", PageRequest.of(0, 2));
        var last = repository.findUserVocabularyForExport("user-1", "fr", PageRequest.of(2, 2));
        assertThat(first.hasNext()).isTrue();
        assertThat(first.getContent()).extracting(UserVocabularyExportProjection::getWordSense)
                .containsExactly("Le fruit du pommier.", "A company");
        assertThat(last.hasNext()).isFalse();
        assertThat(last.getContent()).extracting(UserVocabularyExportProjection::getWordSense)
                .containsExactly("English fallback", "Le fruit du pommier.");
    }

    @Test
    void preservesExplicitSavedLocalizationInsteadOfChoosingAnotherTranslation() {
        vocabulary("saved-7", "user-1", null, "loc-2", 1);
        var result = repository.findUserVocabularyForExport("user-1", "vi", PageRequest.of(0, 500));
        assertThat(result.getContent().getLast().getWordSense()).isEqualTo("Định nghĩa đầy đủ thứ hai.");
    }

    @Test
    void skipsShortOnlyTranslationsAndLeavesMissingFullDefinitionEmpty() {
        localization("loc-0", "sense-1", "vi", "Nghĩa ngắn không được xuất", null);
        localization("loc-short-only", null, "vi", "Nghĩa ngắn riêng", null);
        vocabulary("saved-7", "user-1", null, "loc-short-only", 1);

        var result = repository.findUserVocabularyForExport("user-1", "vi", PageRequest.of(0, 500));

        assertThat(result.getContent().getFirst().getWordSense()).isEqualTo("Một loại quả có vỏ đỏ hoặc xanh.");
        assertThat(result.getContent().getLast().getWordSense()).isEmpty();
    }

    private void sense(String id, String definition) {
        jdbc.update("INSERT INTO word_senses (id, word_id, definition) VALUES (?, 'word-1', ?)", id, definition);
    }

    private void localization(String id, String senseId, String lang, String shortMeaning, String full) {
        jdbc.update("""
                INSERT INTO word_sense_localizations (id, word_id, sense_id, lang_code, short_meaning,
                    full_localized_definition, source, review_status, created_at, updated_at)
                VALUES (?, 'word-1', ?, ?, ?, ?, 'LOCAL', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, senseId, lang, shortMeaning, full);
    }

    private void vocabulary(String id, String userId, String senseId, String localizedId, int level) {
        jdbc.update("""
                INSERT INTO user_vocabularies (id, user_id, word_id, sense_id, sense_localized_id, level,
                    current_level_correct_turns, created_at, updated_at)
                VALUES (?, ?, 'word-1', ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, userId, senseId, localizedId, level);
    }
}
