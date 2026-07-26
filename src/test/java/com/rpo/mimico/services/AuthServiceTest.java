package com.rpo.mimico.services;

import com.rpo.mimico.dtos.LoginRequestDTO;
import com.rpo.mimico.entities.AuthCredentialsEntity;
import com.rpo.mimico.entities.RolesEntity;
import com.rpo.mimico.entities.UserEntity;
import com.rpo.mimico.exceptions.InvalidCredentialsException;
import com.rpo.mimico.repositories.AuthCredentialsRepository;
import com.rpo.mimico.securities.JwtProperties;
import com.rpo.mimico.securities.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthCredentialsRepository authCredentialsRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private JwtProperties jwtProperties;
    @Mock
    private OnlineUsersService onlineUsersService;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                authCredentialsRepository,
                passwordEncoder,
                jwtTokenProvider,
                redisTemplate,
                jwtProperties,
                onlineUsersService
        );
    }

    @Test
    void loginReturnsTokenAndCanonicalUserProfile() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setNickname("player_1");
        user.getRoles().add(new RolesEntity("PLAYER", "Default player"));

        AuthCredentialsEntity credentials = new AuthCredentialsEntity();
        credentials.setEmail("player@example.test");
        credentials.setPasswordHash("hash");
        credentials.setUser(user);

        when(authCredentialsRepository.findByEmail("player@example.test")).thenReturn(Optional.of(credentials));
        when(passwordEncoder.matches("Strong1!", "hash")).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jwtProperties.getExpiration()).thenReturn(7200L);
        when(jwtTokenProvider.generateToken(eq(userId), eq("player@example.test"), anyString(), anyList()))
                .thenReturn("jwt-token");

        var response = authService.login(new LoginRequestDTO("player@example.test", "Strong1!"));

        assertEquals("jwt-token", response.token());
        assertEquals(userId.toString(), response.user().userId());
        assertEquals("player@example.test", response.user().email());
        assertEquals("player_1", response.user().nickname());
        assertTrue(response.user().roles().contains("PLAYER"));
    }

    @Test
    void loginRejectsInvalidCredentials() {
        when(authCredentialsRepository.findByEmail("player@example.test")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequestDTO("player@example.test", "wrong")));
    }

    @Test
    void logoutDeletesSessionAndPresence() {
        UUID userId = UUID.randomUUID();

        authService.logout(userId);

        verify(redisTemplate).delete("session:" + userId);
        verify(onlineUsersService).removeUser(userId);
    }
}
