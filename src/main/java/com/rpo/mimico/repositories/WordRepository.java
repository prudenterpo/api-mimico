package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.WordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WordRepository extends JpaRepository<WordEntity, UUID> {

    @Query(value = "SELECT * FROM words WHERE category_id = :categoryId ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    WordEntity findRandomByCategoryId(@Param("categoryId") UUID categoryId);

    List<WordEntity> findByCategoryId(UUID categoryId);
}