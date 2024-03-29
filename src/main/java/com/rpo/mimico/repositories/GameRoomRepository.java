package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.GameRoom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRoomRepository extends JpaRepository<GameRoom, Long> {
}
