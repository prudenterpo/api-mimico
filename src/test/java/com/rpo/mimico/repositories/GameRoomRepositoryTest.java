package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.GameRoom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
public class GameRoomRepositoryTest {

    @Autowired
    private GameRoomRepository gameRoomRepository;

    @Test
    public void shouldSaveGameRoom() {
        GameRoom room = new GameRoom();
        room.setName("Test Room");
        room.setStatus("active");
        room.setCode("123456");

        GameRoom savedRoom = gameRoomRepository.save(room);

        assertNotNull(savedRoom);
        assertEquals(savedRoom, room);
    }

    @Test
    public void shouldGetGameRoomById() {
        GameRoom room = new GameRoom();
        room.setName("Test Room");
        room.setStatus("active");
        room.setCode("123456");
        gameRoomRepository.save(room);

        GameRoom foundRoom = gameRoomRepository.findById(room.getId()).orElse(null);

        assertNotNull(foundRoom);
        assertEquals(room, foundRoom);
    }

    @Test
    public void shouldUpdateGameRoom() {
        GameRoom room = new GameRoom();
        room.setName("Test Room");
        room.setStatus("active");
        room.setCode("123456");
        gameRoomRepository.save(room);

        room.setStatus("waiting");
        GameRoom updatedRoom = gameRoomRepository.save(room);

        assertNotNull(updatedRoom);
        assertEquals("waiting", updatedRoom.getStatus());
    }

    @Test
    public void shouldDeleteGameRoom() {
        GameRoom room = new GameRoom();
        room.setName("Test Room");
        room.setStatus("active");
        room.setCode("123456");
        gameRoomRepository.save(room);

        gameRoomRepository.delete(room);

        assertTrue(gameRoomRepository.findById(room.getId()).isEmpty());
    }
}
