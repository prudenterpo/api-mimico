package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.GameRoomEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
public class GameEntityRoomRepositoryTest {

    @Autowired
    private GameRoomRepository gameRoomRepository;

    @Test
    public void shouldSaveGameRoom() {
        GameRoomEntity room = new GameRoomEntity();
        room.setName("Test Room");
        room.setStatus("active");
        room.setCode("123456");

        GameRoomEntity savedRoom = gameRoomRepository.save(room);

        assertNotNull(savedRoom);
        assertEquals(savedRoom, room);
    }

    @Test
    public void shouldGetGameRoomById() {
        GameRoomEntity room = new GameRoomEntity();
        room.setName("Test Room");
        room.setStatus("active");
        room.setCode("123456");
        gameRoomRepository.save(room);

        GameRoomEntity foundRoom = gameRoomRepository.findById(room.getId()).orElse(null);

        assertNotNull(foundRoom);
        assertEquals(room, foundRoom);
    }

    @Test
    public void shouldUpdateGameRoom() {
        GameRoomEntity room = new GameRoomEntity();
        room.setName("Test Room");
        room.setStatus("active");
        room.setCode("123456");
        gameRoomRepository.save(room);

        room.setStatus("waiting");
        GameRoomEntity updatedRoom = gameRoomRepository.save(room);

        assertNotNull(updatedRoom);
        assertEquals("waiting", updatedRoom.getStatus());
    }

    @Test
    public void shouldDeleteGameRoom() {
        GameRoomEntity room = new GameRoomEntity();
        room.setName("Test Room");
        room.setStatus("active");
        room.setCode("123456");
        gameRoomRepository.save(room);

        gameRoomRepository.delete(room);

        assertTrue(gameRoomRepository.findById(room.getId()).isEmpty());
    }
}
