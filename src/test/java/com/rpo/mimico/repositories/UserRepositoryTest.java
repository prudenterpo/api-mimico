package com.rpo.mimico.repositories;

import com.rpo.mimico.MimicoApplication;
import com.rpo.mimico.entities.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
public class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    public void testSaveUser() {
        User user = new User();
        user.setUsername("testuser");
        user.setPassword("password");

        User savedUser = userRepository.save(user);

        assertNotNull(savedUser);
        assertNotNull(savedUser.getId());
        assertEquals("testuser", savedUser.getUsername());
    }

    @Test
    public void testSaveUserWithDuplicateUsername() {
        User user1 = new User();
        user1.setUsername("testuser");
        user1.setPassword("password");
        userRepository.save(user1);

        User user2 = new User();
        user2.setUsername("testuser");
        user2.setPassword("password");

        assertThrows(DataIntegrityViolationException.class, () -> userRepository.save(user2));
    }
}
