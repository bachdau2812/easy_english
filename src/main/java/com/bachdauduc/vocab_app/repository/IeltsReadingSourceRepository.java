package com.bachdauduc.vocab_app.repository;

import com.bachdauduc.vocab_app.entity.IeltsReadingSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IeltsReadingSourceRepository extends JpaRepository<IeltsReadingSource, String> {
    @Query(value = """
            SELECT DISTINCT s.name
            FROM ielts_reading_sources s
            WHERE s.name IS NOT NULL
                AND TRIM(s.name) <> ''
            ORDER BY s.name ASC
            """, nativeQuery = true)
    List<String> findDistinctNames();

    @Query(
            value = """
                    SELECT *
                    FROM ielts_reading_sources s
                    WHERE s.name = :name
                    ORDER BY s.created_at DESC, s.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM ielts_reading_sources s
                    WHERE s.name = :name
                    """,
            nativeQuery = true
    )
    Page<IeltsReadingSource> findByName(
            @Param("name") String name,
            Pageable pageable
    );
}
