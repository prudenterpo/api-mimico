package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
public class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    public void shouldSaveUser() {
        User user = new User();
        user.setUsername("testuser");
        user.setPassword("password");

        User savedUser = userRepository.save(user);

        assertNotNull(savedUser);
        assertEquals(savedUser, user);
    }

    @Test
    public void shouldGetUserById() {
        User user = new User();
        user.setUsername("testuser");
        user.setPassword("password");
        userRepository.save(user);

        User foundUser = userRepository.findById(user.getId()).orElse(null);

        assertNotNull(foundUser);
        assertEquals(user, foundUser);
    }

    @Test
    public void shouldSaveUserWithDuplicateUsernameShouldThrowException() {
        User user1 = new User();
        user1.setUsername("testuser");
        user1.setPassword("password");
        userRepository.save(user1);

        User user2 = new User();
        user2.setUsername("testuser");
        user2.setPassword("password");

        assertThrows(DataIntegrityViolationException.class, () -> userRepository.save(user2));
    }

    @Test
    public void shouldUpdateUser() {
        User user = new User();
        user.setUsername("testuser");
        user.setPassword("password");
        userRepository.save(user);

        user.setUsername("updateduser");
        User updatedUser = userRepository.save(user);

        assertNotNull(updatedUser);
        assertEquals("updateduser", updatedUser.getUsername());
    }

    @Test
    public void shouldDeleteUser() {
        User user = new User();
        user.setUsername("testuser");
        user.setPassword("password");
        userRepository.save(user);

        userRepository.delete(user);

        assertTrue(userRepository.findById(user.getId()).isEmpty());
    }
}
