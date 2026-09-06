package com.bachdauduc.vocab_app.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ListenAndTypeTranslationRepositoryTest {
    @Autowired ListenAndTypeExerciseChallengeRepository repository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void savesTranslationWithoutOverwritingOtherChallengeFieldsOrExistingTranslation() {
        insertChallenge("challenge", "Hello.", null);

        assertThat(repository.saveTranslationIfMissing("challenge", "Hello.", "Xin chào.")).isEqualTo(1);
        assertThat(repository.saveTranslationIfMissing("challenge", "Hello.", "Overwrite")).isZero();
        var saved = repository.findById("challenge").orElseThrow();
        assertThat(saved.getTranslate()).isEqualTo("Xin chào.");
        assertThat(saved.getContent()).isEqualTo("Hello.");
        assertThat(saved.getSolution()).isEqualTo("Different answer");
        assertThat(saved.getPosition()).isEqualTo(1);
    }

    @Test
    void handlesBlankTranslationAndRejectsTranslationForChangedContent() {
        insertChallenge("challenge-blank", "Updated content", "   ");

        assertThat(repository.saveTranslationIfMissing("challenge-blank", "Old content", "Outdated")).isZero();
        assertThat(repository.saveTranslationIfMissing("challenge-blank", "Updated content", "Bản dịch mới")).isEqualTo(1);
        assertThat(repository.findById("challenge-blank").orElseThrow().getTranslate()).isEqualTo("Bản dịch mới");
    }

    @Test
    void savesDocumentTranslationWhenContentIsNullAndRejectsChangedContent() {
        insertChallenge("null-content", null, null);
        insertChallenge("changed-content", "New content", null);

        assertThat(repository.saveTranslationIfMissing("null-content", null, "Document translation")).isEqualTo(1);
        assertThat(repository.saveTranslationIfMissing("null-content", null, "Overwrite")).isZero();
        assertThat(repository.saveTranslationIfMissing("changed-content", null, "Stale translation")).isZero();
        assertThat(repository.findById("null-content").orElseThrow().getTranslate()).isEqualTo("Document translation");
    }

    private void insertChallenge(String id, String content, String translation) {
        jdbc.update("""
                INSERT INTO listen_and_type_exercise_challenges
                    (id, listen_exercise_id, solution, translate, content, position, created_at, updated_at)
                VALUES (?, 'lesson', 'Different answer', ?, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, translation, content);
    }
}
