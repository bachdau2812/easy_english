package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.WordIdiomTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WordIdiomTranslationRepository extends JpaRepository<WordIdiomTranslation, String> {
    List<WordIdiomTranslation> findByLangCode(String langCode);

    Optional<WordIdiomTranslation> findFirstByIdiomIdAndLangCode(String idiomId, String langCode);
}
