package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.MatchEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchRepository extends JpaRepository<MatchEntity, UUID> {

    Optional<MatchEntity> findByTableIdAndFinishedAtIsNull(UUID tableId);

    Optional<MatchEntity> findFirstByTable_IdOrderByStartedAtDesc(UUID tableId);

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT m FROM MatchEntity m WHERE m.id = :id")
    Optional<MatchEntity> findForUpdate(@Param("id") UUID id);
}