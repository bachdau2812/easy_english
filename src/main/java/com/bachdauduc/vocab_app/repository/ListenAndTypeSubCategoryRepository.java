package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.ListenAndTypeSubCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ListenAndTypeSubCategoryRepository extends JpaRepository<ListenAndTypeSubCategory, Long> {
    List<ListenAndTypeSubCategory> findByCategoryIdOrderBySubCategoryNameAsc(String categoryId);

    List<ListenAndTypeSubCategory> findByCategoryId(String categoryId);
}
