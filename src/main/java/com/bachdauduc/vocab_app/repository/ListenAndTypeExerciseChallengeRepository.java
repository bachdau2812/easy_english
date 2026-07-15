package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.ListenAndTypeExerciseChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ListenAndTypeExerciseChallengeRepository extends JpaRepository<ListenAndTypeExerciseChallenge, String> {
    List<ListenAndTypeExerciseChallenge> findByListenExerciseIdOrderByPositionAsc(String listenExerciseId);

    long countByListenExerciseId(String listenExerciseId);
}
