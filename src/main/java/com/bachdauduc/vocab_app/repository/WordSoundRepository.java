package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.WordSound;
import com.bachdauduc.vocab_app.repository.projection.WordSoundProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WordSoundRepository extends JpaRepository<WordSound, String> {
    List<WordSound> findByWordId(String wordId);

    List<WordSound> findByWordIdAndSoundSource(String wordId, String soundSource);

    @Query(value = """
            SELECT
                w.id AS wordId,
                ws.ipa AS ipa,
                ws.tags AS tags,
                ws.sound_source AS soundSource,
                ws.ogg_url AS oggUrl,
                ws.mp3_url AS mp3Url,
                ws.enpr AS enpr
            FROM words w
            JOIN word_sounds ws ON w.id = ws.word_id
            WHERE w.id = :wordId
            """, nativeQuery = true)
    List<WordSoundProjection> findWordSounds(@Param("wordId") String wordId);

    @Query(value = """
            SELECT
                w.id AS wordId,
                ws.ipa AS ipa,
                ws.tags AS tags,
                ws.sound_source AS soundSource,
                ws.ogg_url AS oggUrl,
                ws.mp3_url AS mp3Url,
                ws.enpr AS enpr
            FROM words w
            JOIN word_sounds ws ON w.id = ws.word_id
            WHERE w.id = :wordId
                AND ws.sound_source = :soundSource
            """, nativeQuery = true)
    List<WordSoundProjection> findWordSoundsBySource(
            @Param("wordId") String wordId,
            @Param("soundSource") String soundSource
    );
}
