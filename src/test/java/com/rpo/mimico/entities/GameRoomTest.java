package com.rpo.mimico.entities;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GameRoomTest {

    @Test
    public void testGameRoomCreation() {
        GameRoom gameRoom = new GameRoom();
        gameRoom.setName("Test Room");
        gameRoom.setStatus("active");
        gameRoom.setCode("12345");

        assertEquals("Test Room", gameRoom.getName());
        assertEquals("active", gameRoom.getStatus());
        assertEquals("12345", gameRoom.getCode());
    }
}
