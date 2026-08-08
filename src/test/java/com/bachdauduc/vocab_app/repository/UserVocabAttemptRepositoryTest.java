package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.constant.ExerciseType;
import com.bachdauduc.vocab_app.repository.projection.UserVocabStatisticProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserVocabAttemptRepositoryTest {
    @Autowired
    UserVocabAttemptRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void getUserDailyStatisticCountsQuizAndLatButNotVocabularyOrWriting() {
        String userId = "user-1";
        LocalDate today = LocalDate.of(2026, 7, 18);
        LocalDateTime fromTime = today.atStartOfDay();
        LocalDateTime toTime = today.plusDays(1).atStartOfDay();

        insertAttempt(userId, "quiz-1", null, "QUIZ_IELTS_READING", true, fromTime.plusHours(1));
        insertAttempt(userId, "lat-1", null, "LAT_LISTEN_AND_TYPE", false, fromTime.plusHours(2));
        insertAttempt(userId, "vocab-1", "user-vocab-1", ExerciseType.VOCAB_WORD_TO_MEANING.name(), true, fromTime.plusHours(3));
        insertAttempt(userId, "writing-1", null, ExerciseType.IELTS_WRITING_REVIEW.name(), true, fromTime.plusHours(4));

        UserVocabStatisticProjection statistic = repository.getUserDailyStatistic(userId, fromTime, toTime);

        assertThat(statistic.getTotalAttempts()).isEqualTo(2L);
        assertThat(statistic.getCorrectQuizAttempt()).isEqualTo(1L);
        assertThat(statistic.getWrongQuizAttempt()).isEqualTo(1L);
        assertThat(statistic.getTotalUniqueVocab()).isEqualTo(1L);
        assertThat(statistic.getCorrectUniqueVocab()).isEqualTo(1L);
        assertThat(statistic.getWrongUniqueVocab()).isEqualTo(0L);
        assertThat(statistic.getWrongCountVocab()).isNull();
    }

    @Test
    void getUserOverallStatisticCountsQuizAndLatButNotVocabularyOrWriting() {
        String userId = "user-1";
        LocalDateTime baseTime = LocalDate.of(2026, 7, 18).atStartOfDay();

        insertAttempt(userId, "quiz-1", null, "QUIZ_IELTS_READING", true, baseTime.plusHours(1));
        insertAttempt(userId, "lat-1", null, "LAT_LISTEN_AND_TYPE", false, baseTime.plusHours(2));
        insertAttempt(userId, "vocab-1", "user-vocab-1", ExerciseType.VOCAB_WORD_TO_MEANING.name(), true, baseTime.plusHours(3));
        insertAttempt(userId, "writing-1", null, ExerciseType.IELTS_WRITING_REVIEW.name(), true, baseTime.plusHours(4));

        UserVocabStatisticProjection statistic = repository.getUserOverallStatistic(userId);

        assertThat(statistic.getTotalAttempts()).isEqualTo(2L);
        assertThat(statistic.getCorrectQuizAttempt()).isEqualTo(1L);
        assertThat(statistic.getWrongQuizAttempt()).isEqualTo(1L);
        assertThat(statistic.getTotalUniqueVocab()).isEqualTo(1L);
        assertThat(statistic.getCorrectUniqueVocab()).isNull();
        assertThat(statistic.getWrongUniqueVocab()).isNull();
        assertThat(statistic.getWrongCountVocab()).isEqualTo(0L);
    }

    private void insertAttempt(
            String userId,
            String attemptId,
            String userVocabId,
            String exerciseType,
            boolean correct,
            LocalDateTime createdAt
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO user_vocab_attempts (
                            id, attempt_id, user_id, user_vocab_id, exercise_type, user_answer, review, is_correct, replay_count, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                UUID.randomUUID().toString(),
                attemptId,
                userId,
                userVocabId,
                exerciseType,
                "answer",
                null,
                correct,
                0,
                Timestamp.valueOf(createdAt)
        );
    }
}