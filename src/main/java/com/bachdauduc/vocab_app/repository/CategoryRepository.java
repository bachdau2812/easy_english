package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, String> {
    List<Category> findByIdIn(List<String> ids);

    Optional<Category> findFirstBySlug(String slug);

    List<Category> findAllByOrderByNameAsc();
}
