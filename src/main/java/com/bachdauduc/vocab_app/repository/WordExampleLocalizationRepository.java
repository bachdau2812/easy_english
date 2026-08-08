package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.WordExampleLocalization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

public interface WordExampleLocalizationRepository extends JpaRepository<WordExampleLocalization, String> {
    List<WordExampleLocalization> findByExampleIdInAndLangCode(Collection<String> exampleIds, String langCode);

    List<WordExampleLocalization> findByWordIdAndLangCode(String wordId, String langCode);

    Optional<WordExampleLocalization> findFirstByExampleIdAndLangCode(String exampleId, String langCode);
}
