package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.MatchPlayerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MatchPlayerRepository extends JpaRepository<MatchPlayerEntity, UUID> {

    List<MatchPlayerEntity> findByMatchIdOrderByPlayerOrder(UUID matchId);

    List<MatchPlayerEntity> findByMatchIdAndTeam(UUID matchId, Character team);
}