package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.IeltsWritingReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IeltsWritingReferenceRepository extends JpaRepository<IeltsWritingReference, String> {
    @Query(value = """
            SELECT DISTINCT r.band
            FROM ielts_writing_reference r
            WHERE r.ielts_writing_exercise_id = :problemId
                AND r.band IS NOT NULL
                AND TRIM(r.band) <> ''
            ORDER BY r.band ASC
            """, nativeQuery = true)
    List<String> findDistinctBandsByProblemId(@Param("problemId") String problemId);

    @Query(value = """
            SELECT *
            FROM ielts_writing_reference r
            WHERE r.ielts_writing_exercise_id = :problemId
                AND r.band = :band
            ORDER BY r.created_at DESC, r.id DESC
            """, nativeQuery = true)
    List<IeltsWritingReference> findByProblemIdAndBand(
            @Param("problemId") String problemId,
            @Param("band") String band
    );
}
