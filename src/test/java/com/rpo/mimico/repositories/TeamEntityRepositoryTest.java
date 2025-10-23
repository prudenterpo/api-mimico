package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.TeamEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
public class TeamEntityRepositoryTest {

    @Autowired
    private TeamRepository teamRepository;

    @Test
    public void shouldSaveTeam() {
        TeamEntity teamEntity = new TeamEntity();
        teamEntity.setName("Test TeamEntity");
        teamEntity.setScore(100);

        TeamEntity savedTeamEntity = teamRepository.save(teamEntity);

        assertNotNull(savedTeamEntity);
        assertEquals(savedTeamEntity, teamEntity);
    }

    @Test
    public void shouldGetTeamById() {
        TeamEntity teamEntity = new TeamEntity();
        teamEntity.setName("Test TeamEntity");
        teamEntity.setScore(100);
        teamRepository.save(teamEntity);

        TeamEntity foundTeamEntity = teamRepository.findById(teamEntity.getId()).orElse(null);

        assertNotNull(foundTeamEntity);
        assertEquals(teamEntity, foundTeamEntity);
    }

    @Test
    public void shouldUpdateTeam() {
        TeamEntity teamEntity = new TeamEntity();
        teamEntity.setName("Test TeamEntity");
        teamEntity.setScore(100);
        teamRepository.save(teamEntity);

        teamEntity.setScore(150);
        TeamEntity updatedTeamEntity = teamRepository.save(teamEntity);

        assertNotNull(updatedTeamEntity);
        assertEquals(150, updatedTeamEntity.getScore());
    }

    @Test
    public void shoulDeleteTeam() {
        TeamEntity teamEntity = new TeamEntity();
        teamEntity.setName("Test TeamEntity");
        teamEntity.setScore(100);
        teamRepository.save(teamEntity);

        teamRepository.delete(teamEntity);

        assertTrue(teamRepository.findById(teamEntity.getId()).isEmpty());
    }
}
