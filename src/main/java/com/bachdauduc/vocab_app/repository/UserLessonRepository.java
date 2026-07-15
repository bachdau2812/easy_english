package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.UserLesson;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserLessonRepository extends JpaRepository<UserLesson, String> {
}
