package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.Player;
import com.rpo.mimico.entities.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
public class PlayerRepositoryTest {

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    public void shouldSavePlayer() {

        User user = new User();
        user.setUsername("testuser");
        user.setPassword("password");
        userRepository.save(user);

        Player player = new Player();
        player.setUser(user);

        Player savedPlayer = playerRepository.save(player);

        assertNotNull(savedPlayer);
        assertEquals(savedPlayer, player);
    }

    @Test
    public void shouldGetPlayerById() {

        User user = new User();
        user.setUsername("testuser");
        user.setPassword("password");
        userRepository.save(user);

        Player player = new Player();
        player.setUser(user);
        Player savedPlayer = playerRepository.save(player);

        Player foundPlayer = playerRepository.findById(savedPlayer.getId()).orElse(null);

        assertNotNull(foundPlayer);
        assertEquals(savedPlayer, foundPlayer);
    }

    @Test
    public void shouldUpdatePlayer() {

        User user = new User();
        user.setUsername("testuser");
        user.setPassword("password");
        userRepository.save(user);

        Player player = new Player();
        player.setUser(user);
        Player savedPlayer = playerRepository.save(player);

        User user1 = new User();
        user1.setUsername("testuser1");
        user1.setPassword("password1");
        userRepository.save(user1);

        savedPlayer.setUser(user1);
        Player updatedPlayer = playerRepository.save(savedPlayer);

        assertNotNull(updatedPlayer);
        assertEquals(user1, updatedPlayer.getUser());
    }

    @Test
    public void shouldDeletePlayer() {
        User user = new User();
        user.setUsername("testuser");
        user.setPassword("password");
        userRepository.save(user);

        Player player = new Player();
        player.setUser(user);
        Player savedPlayer = playerRepository.save(player);

        playerRepository.delete(savedPlayer);

        assertTrue(playerRepository.findById(savedPlayer.getId()).isEmpty());
    }
}
