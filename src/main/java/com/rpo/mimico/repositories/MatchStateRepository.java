package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.MatchStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchStateRepository extends JpaRepository<MatchStateEntity, UUID> {

    Optional<MatchStateEntity> findByMatchId(UUID matchId);

    @Query("SELECT ms FROM MatchStateEntity ms " +
            "WHERE ms.roundExpiresAt IS NOT NULL " +
            "AND ms.roundExpiresAt < :now " +
            "AND ms.isPaused = false")
    List<MatchStateEntity> findExpiredRounds(@Param("now")LocalDateTime now);


}
