package com.rpo.mimico.entities;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GameTest {

    @Test
    public void testGameCreation() {
        Game game = new Game();
        LocalDateTime now = LocalDateTime.now();
        game.setStartTime(now);
        game.setEndTime(now.plusHours(1));
        game.setGameRoom(new GameRoom());  // Assuming a GameRoom is required
        game.setWinnerTeam(new Team());    // Assuming a Team can be set as winner

        assertNotNull(game.getStartTime());
        assertNotNull(game.getEndTime());
        assertNotNull(game.getGameRoom());
        assertNotNull(game.getWinnerTeam());
    }
}
