package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.Player;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Long> {
}
