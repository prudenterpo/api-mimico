package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.Game;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<Game, Long> {
}
