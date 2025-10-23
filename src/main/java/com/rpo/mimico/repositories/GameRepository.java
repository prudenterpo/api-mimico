package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.GameEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<GameEntity, Long> {
}
