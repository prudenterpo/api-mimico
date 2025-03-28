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
        game.setGameRoom(new GameRoom());  
        game.setWinnerTeam(new Team());    

        assertNotNull(game.getStartTime());
        assertNotNull(game.getEndTime());
        assertNotNull(game.getGameRoom());
        assertNotNull(game.getWinnerTeam());
    }
}
