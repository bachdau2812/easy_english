package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.WordSenseLocalization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WordSenseLocalizationRepository extends JpaRepository<WordSenseLocalization, String> {
    List<WordSenseLocalization> findByWordIdAndLangCode(String wordId, String langCode);

    List<WordSenseLocalization> findByWordIdAndSource(String wordId, String source);

    Optional<WordSenseLocalization> findFirstBySenseIdAndLangCode(String senseId, String langCode);

    boolean existsByIdAndWordId(String id, String wordId);
}
