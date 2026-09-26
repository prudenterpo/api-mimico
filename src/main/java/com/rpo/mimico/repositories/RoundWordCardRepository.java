package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.RoundWordCardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RoundWordCardRepository extends JpaRepository<RoundWordCardEntity, UUID> {

    List<RoundWordCardEntity> findByRound_IdOrderBySelectionOrderAsc(UUID roundId);
}
