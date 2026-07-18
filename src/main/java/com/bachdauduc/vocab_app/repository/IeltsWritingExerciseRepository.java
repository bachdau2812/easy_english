package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.IeltsWritingExercise;
import com.bachdauduc.vocab_app.repository.projection.IeltsWritingProblemSummaryProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IeltsWritingExerciseRepository extends JpaRepository<IeltsWritingExercise, String> {
    @Query(value = """
            SELECT DISTINCT e.problem_topic
            FROM ielts_writing_exercise e
            WHERE e.task_type = :taskType
                AND e.problem_topic IS NOT NULL
                AND TRIM(e.problem_topic) <> ''
            ORDER BY e.problem_topic ASC
            """, nativeQuery = true)
    List<String> findDistinctTopicsByTaskType(@Param("taskType") Integer taskType);

    @Query(value = """
            SELECT
                e.id AS id,
                e.problem AS problem,
                COUNT(a.id) AS doneCount
            FROM ielts_writing_exercise e
            LEFT JOIN user_vocab_attempts a
                ON e.id = a.attempt_id
                AND a.user_id = :userId
            WHERE e.problem_topic = :topicName
            GROUP BY e.id, e.problem, e.created_at
            ORDER BY e.created_at DESC, e.id DESC
            """, nativeQuery = true)
    List<IeltsWritingProblemSummaryProjection> findProblemSummariesByTopic(
            @Param("topicName") String topicName,
            @Param("userId") String userId
    );
}