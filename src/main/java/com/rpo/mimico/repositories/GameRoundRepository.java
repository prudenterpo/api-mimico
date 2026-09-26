package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.GameRoundEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GameRoundRepository extends JpaRepository<GameRoundEntity, UUID> {

    Optional<GameRoundEntity> findFirstByMatch_IdOrderByRoundNumberDesc(UUID matchId);
}
