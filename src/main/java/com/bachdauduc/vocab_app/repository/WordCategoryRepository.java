package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.WordCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WordCategoryRepository extends JpaRepository<WordCategory, String> {
    boolean existsByWordIdAndCategoryId(String wordId, String categoryId);

    List<WordCategory> findByWordId(String wordId);
}
