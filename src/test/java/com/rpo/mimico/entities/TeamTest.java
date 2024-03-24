package com.rpo.mimico.entities;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TeamTest {

    @Test
    public void testTeamCreation() {
        Team team = new Team();
        team.setName("Test Team");
        team.setScore(10);

        assertEquals("Test Team", team.getName());
        assertEquals(10, team.getScore());
    }
}
