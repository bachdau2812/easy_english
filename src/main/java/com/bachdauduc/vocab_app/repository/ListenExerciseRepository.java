package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.ListenExercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ListenExerciseRepository extends JpaRepository<ListenExercise, String> {
    List<ListenExercise> findByCategoryId(String categoryId);

    List<ListenExercise> findBySubCategory(String subCategory);

    List<ListenExercise> findBySubCategoryIn(List<String> subCategories);
}
