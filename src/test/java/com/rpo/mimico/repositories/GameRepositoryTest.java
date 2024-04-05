package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.Game;
import com.rpo.mimico.entities.GameRoom;
import com.rpo.mimico.entities.Team;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
public class GameRepositoryTest {

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GameRoomRepository gameRoomRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Test
    public void shouldSaveGame() {
        Game game = new Game();
        GameRoom gameRoom = new GameRoom();
        Team team = new Team();
        team.setName("Test Team");
        Team savedTeam = teamRepository.save(team);
        GameRoom savedGameRoom = gameRoomRepository.save(gameRoom);
        game.setWinnerTeam(savedTeam);
        game.setGameRoom(savedGameRoom);
        game.setStartTime(LocalDateTime.now());
        game.setEndTime(LocalDateTime.now().plusMinutes(10));
        game.setWinnerTeam(new Team());

        Game savedGame = gameRepository.save(game);

        assertNotNull(savedGame);
        assertEquals(savedGame, game);
    }

    @Test
    public void shouldGetGameById() {
        Game game = new Game();
        GameRoom gameRoom = new GameRoom();
        Team team = new Team();
        team.setName("Test Team");
        Team savedTeam = teamRepository.save(team);
        GameRoom savedGameRoom = gameRoomRepository.save(gameRoom);
        game.setWinnerTeam(savedTeam);
        game.setGameRoom(savedGameRoom);
        game.setStartTime(LocalDateTime.now());
        game.setEndTime(LocalDateTime.now().plusMinutes(10));
        game.setWinnerTeam(new Team());
        gameRepository.save(game);

        Game foundGame = gameRepository.findById(game.getId()).orElse(null);

        assertNotNull(foundGame);
        assertEquals(game, foundGame);
    }

    @Test
    public void shouldUpdateGame() {
        Game game = new Game();
        GameRoom gameRoom = new GameRoom();
        Team team = new Team();
        team.setName("Test Team");
        Team savedTeam = teamRepository.save(team);
        GameRoom savedGameRoom = gameRoomRepository.save(gameRoom);
        game.setWinnerTeam(savedTeam);
        game.setGameRoom(savedGameRoom);
        game.setStartTime(LocalDateTime.now());
        game.setEndTime(LocalDateTime.now().plusMinutes(10));
        game.setWinnerTeam(new Team());
        gameRepository.save(game);

        game.setEndTime(LocalDateTime.now().plusMinutes(20));
        Game updatedGame = gameRepository.save(game);

        assertNotNull(updatedGame);
    }

    @Test
    public void shouldDeleteGame() {
        Game game = new Game();
        GameRoom gameRoom = new GameRoom();
        Team team = new Team();
        team.setName("Test Team");
        Team savedTeam = teamRepository.save(team);
        GameRoom savedGameRoom = gameRoomRepository.save(gameRoom);
        game.setWinnerTeam(savedTeam);
        game.setGameRoom(savedGameRoom);
        game.setStartTime(LocalDateTime.now());
        game.setEndTime(LocalDateTime.now().plusMinutes(10));
        game.setWinnerTeam(new Team());
        gameRepository.save(game);

        gameRepository.delete(game);

        assertEquals(0, gameRepository.count());
    }
}
