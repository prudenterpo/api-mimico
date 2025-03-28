package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.Team;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
public class TeamRepositoryTest {

    @Autowired
    private TeamRepository teamRepository;

    @Test
    public void shouldSaveTeam() {
        Team team = new Team();
        team.setName("Test Team");
        team.setScore(100);

        Team savedTeam = teamRepository.save(team);

        assertNotNull(savedTeam);
        assertEquals( savedTeam, team);
    }

    @Test
    public void shouldGetTeamById() {
        Team team = new Team();
        team.setName("Test Team");
        team.setScore(100);
        teamRepository.save(team);

        Team foundTeam = teamRepository.findById(team.getId()).orElse(null);

        assertNotNull(foundTeam);
        assertEquals(team, foundTeam);
    }

    @Test
    public void shouldUpdateTeam() {
        Team team = new Team();
        team.setName("Test Team");
        team.setScore(100);
        teamRepository.save(team);

        team.setScore(150);
        Team updatedTeam = teamRepository.save(team);

        assertNotNull(updatedTeam);
        assertEquals(150, updatedTeam.getScore());
    }

    @Test
    public void shoulDeleteTeam() {
        Team team = new Team();
        team.setName("Test Team");
        team.setScore(100);
        teamRepository.save(team);

        teamRepository.delete(team);

        assertTrue(teamRepository.findById(team.getId()).isEmpty());
    }
}
