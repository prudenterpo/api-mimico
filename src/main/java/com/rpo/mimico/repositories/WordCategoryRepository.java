package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.WordCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WordCategoryRepository extends JpaRepository<WordCategoryEntity, UUID> {

    Optional<WordCategoryEntity> findByName(String name);
}