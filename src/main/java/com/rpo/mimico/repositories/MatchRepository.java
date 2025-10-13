package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.MatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchRepository extends JpaRepository<MatchEntity, UUID> {

    Optional<MatchEntity> findByTableIdAndFinishedAtIsNull(UUID tableId);
}