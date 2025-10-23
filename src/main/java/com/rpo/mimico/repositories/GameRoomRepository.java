package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.GameRoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRoomRepository extends JpaRepository<GameRoomEntity, Long> {
}
