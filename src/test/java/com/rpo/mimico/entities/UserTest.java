package com.rpo.mimico.entities;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class UserTest {

    @Test
    public void testUserCreation() {
        User user = new User();
        user.setUsername("testUser");
        user.setPassword("password");

        assertEquals("testUser", user.getUsername());
        assertEquals("password", user.getPassword());
    }
}
