package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.MatchStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchStateRepository extends JpaRepository<MatchStateEntity, UUID> {

    Optional<MatchStateEntity> fincByMatchId(UUID matchId);
}
