package com.rpo.mimico.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpo.mimico.dtos.RegisterResponseDTO;
import com.rpo.mimico.dtos.UserProfileDTO;
import com.rpo.mimico.exceptions.GlobalExceptionHandler;
import com.rpo.mimico.services.AuthService;
import com.rpo.mimico.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private UserService userService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService, userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void registerSucceedsWithCanonicalResponse() throws Exception {
        when(userService.register(any())).thenReturn(
                new RegisterResponseDTO(UUID.randomUUID().toString(), "player@example.test", "player_1")
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "player@example.test",
                                "password", "Strong1!",
                                "nickname", "player_1"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value("player@example.test"))
                .andExpect(jsonPath("$.nickname").value("player_1"))
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void registerRejectsInvalidEmailWithCanonicalError() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "invalid-email",
                                "password", "Strong1!",
                                "nickname", "player_1"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Invalid request parameters"))
                .andExpect(jsonPath("$.details.fields", hasKey("email")));
    }

    @Test
    void registerRejectsInvalidNicknameAndWeakPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "player@example.test",
                                "password", "weakpass",
                                "nickname", "bad nickname"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.fields", hasKey("nickname")))
                .andExpect(jsonPath("$.details.fields", hasKey("password")));
    }

    @Test
    void meReturnsCurrentUserProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.getUserProfile(userId)).thenReturn(
                new UserProfileDTO(userId.toString(), "player@example.test", "player_1", null, Set.of("PLAYER"), null)
        );

        mockMvc.perform(get("/api/auth/me")
                        .principal(new UsernamePasswordAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("player@example.test"))
                .andExpect(jsonPath("$.roles[0]").value("PLAYER"));
    }

    @Test
    void logoutClearsServerSideSessionAndReturnsNoContent() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/auth/logout")
                        .principal(new UsernamePasswordAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isNoContent());

        verify(authService).logout(userId);
    }
}
