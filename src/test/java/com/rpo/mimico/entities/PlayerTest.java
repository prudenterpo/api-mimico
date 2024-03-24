package com.rpo.mimico.entities;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PlayerTest {

    @Test
    public void testPlayerCreation() {
        Player player = new Player();
        // Assuming User, GameRoom, and Team are required for a player
        // These would be mocked in a real test environment
        User user = new User();
        GameRoom gameRoom = new GameRoom();
        Team team = new Team();

        player.setUser(user);
        player.setGameRoom(gameRoom);
        player.setTeam(team);

        assertNotNull(player.getUser());
        assertNotNull(player.getGameRoom());
        assertNotNull(player.getTeam());
    }
}
