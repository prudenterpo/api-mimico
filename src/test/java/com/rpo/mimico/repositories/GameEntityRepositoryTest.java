package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.GameEntity;
import com.rpo.mimico.entities.GameRoomEntity;
import com.rpo.mimico.entities.TeamEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
public class GameEntityRepositoryTest {

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GameRoomRepository gameRoomRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Test
    public void shouldSaveGame() {
        GameEntity gameEntity = new GameEntity();
        GameRoomEntity gameRoomEntity = new GameRoomEntity();
        TeamEntity teamEntity = new TeamEntity();
        teamEntity.setName("Test Team");
        TeamEntity savedTeamEntity = teamRepository.save(teamEntity);
        GameRoomEntity savedGameRoomEntity = gameRoomRepository.save(gameRoomEntity);
        gameEntity.setWinnerTeamEntity(savedTeamEntity);
        gameEntity.setGameRoomEntity(savedGameRoomEntity);
        gameEntity.setStartTime(LocalDateTime.now());
        gameEntity.setEndTime(LocalDateTime.now().plusMinutes(10));
        gameEntity.setWinnerTeamEntity(new TeamEntity());

        GameEntity savedGameEntity = gameRepository.save(gameEntity);

        assertNotNull(savedGameEntity);
        assertEquals(savedGameEntity, gameEntity);
    }

    @Test
    public void shouldGetGameById() {
        GameEntity gameEntity = new GameEntity();
        GameRoomEntity gameRoomEntity = new GameRoomEntity();
        TeamEntity teamEntity = new TeamEntity();
        teamEntity.setName("Test TeamEntity");
        TeamEntity savedTeamEntity = teamRepository.save(teamEntity);
        GameRoomEntity savedGameRoomEntity = gameRoomRepository.save(gameRoomEntity);
        gameEntity.setWinnerTeamEntity(savedTeamEntity);
        gameEntity.setGameRoomEntity(savedGameRoomEntity);
        gameEntity.setStartTime(LocalDateTime.now());
        gameEntity.setEndTime(LocalDateTime.now().plusMinutes(10));
        gameEntity.setWinnerTeamEntity(new TeamEntity());
        gameRepository.save(gameEntity);

        GameEntity foundGameEntity = gameRepository.findById(gameEntity.getId()).orElse(null);

        assertNotNull(foundGameEntity);
        assertEquals(gameEntity, foundGameEntity);
    }

    @Test
    public void shouldUpdateGame() {
        GameEntity gameEntity = new GameEntity();
        GameRoomEntity gameRoomEntity = new GameRoomEntity();
        TeamEntity teamEntity = new TeamEntity();
        teamEntity.setName("Test TeamEntity");
        TeamEntity savedTeamEntity = teamRepository.save(teamEntity);
        GameRoomEntity savedGameRoomEntity = gameRoomRepository.save(gameRoomEntity);
        gameEntity.setWinnerTeamEntity(savedTeamEntity);
        gameEntity.setGameRoomEntity(savedGameRoomEntity);
        gameEntity.setStartTime(LocalDateTime.now());
        gameEntity.setEndTime(LocalDateTime.now().plusMinutes(10));
        gameEntity.setWinnerTeamEntity(new TeamEntity());
        gameRepository.save(gameEntity);

        gameEntity.setEndTime(LocalDateTime.now().plusMinutes(20));
        GameEntity updatedGameEntity = gameRepository.save(gameEntity);

        assertNotNull(updatedGameEntity);
    }

    @Test
    public void shouldDeleteGame() {
        GameEntity gameEntity = new GameEntity();
        GameRoomEntity gameRoomEntity = new GameRoomEntity();
        TeamEntity teamEntity = new TeamEntity();
        teamEntity.setName("Test TeamEntity");
        TeamEntity savedTeamEntity = teamRepository.save(teamEntity);
        GameRoomEntity savedGameRoomEntity = gameRoomRepository.save(gameRoomEntity);
        gameEntity.setWinnerTeamEntity(savedTeamEntity);
        gameEntity.setGameRoomEntity(savedGameRoomEntity);
        gameEntity.setStartTime(LocalDateTime.now());
        gameEntity.setEndTime(LocalDateTime.now().plusMinutes(10));
        gameEntity.setWinnerTeamEntity(new TeamEntity());
        gameRepository.save(gameEntity);

        gameRepository.delete(gameEntity);

        assertEquals(0, gameRepository.count());
    }
}
