package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.MatchPlayerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MatchPlayerRepository extends JpaRepository<MatchPlayerEntity, UUID> {

    List<MatchPlayerEntity> findByMatchIdOrderByPlayerOrder(UUID matchId);

    List<MatchPlayerEntity> findByMatchIdAndTeam(UUID matchId, Character team);

    @Query("SELECT mp FROM MatchPlayerEntity mp " +
            "WHERE mp.user.id = :userId " +
            "AND mp.match.finishedAt IS NULL")
    List<MatchPlayerEntity> findActiveMatchesByUserId(@Param("userId") UUID userId);
}