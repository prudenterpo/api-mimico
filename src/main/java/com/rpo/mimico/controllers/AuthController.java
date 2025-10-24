package com.rpo.mimico.controllers;

import com.rpo.mimico.dtos.LoginRequestDTO;
import com.rpo.mimico.dtos.LoginResponseDTO;
import com.rpo.mimico.dtos.RegisterRequestDTO;
import com.rpo.mimico.dtos.RegisterResponseDTO;
import com.rpo.mimico.dtos.UserProfileDTO;
import com.rpo.mimico.services.AuthService;
import com.rpo.mimico.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Handles user login")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @Operation(
            summary = "User registration",
            description = "Creates a new user account with email, password and nickname.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "User created successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid input data"),
                    @ApiResponse(responseCode = "409", description = "Email or nickname already exists")
            }
    )
    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDTO> register(@Valid @RequestBody RegisterRequestDTO request) {
        RegisterResponseDTO response = userService.register(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    @Operation(
            summary = "User login",
            description = "Authenticates user and returns a JWT token.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Login successful"),
                    @ApiResponse(responseCode = "401", description = "Invalid credentials")
            }
    )
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(
            summary = "User logout",
            description = "Invalidates the current user session by removing it from Redis.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Logout successful"),
                    @ApiResponse(responseCode = "401", description = "Not authenticated")
            }
    )
    @PostMapping("/logout")
    public ResponseEntity<String> logout(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        authService.logout(userId);

        return ResponseEntity.ok("Logout successful");
    }

    @Operation(
            summary = "Get current user profile",
            description = "Returns the profile information of the currently authenticated ",
            responses = {
                    @ApiResponse(responseCode = "200", description = "User profile retrieved successfully"),
                    @ApiResponse(responseCode = "401", description = "Not authenticated"),
                    @ApiResponse(responseCode = "404", description = "User not found")
            }
    )
    @GetMapping("/me")
    public ResponseEntity<UserProfileDTO> getCurrentUser(Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        UserProfileDTO userProfile = userService.getUserProfile(userId);

        return ResponseEntity.ok(userProfile);
    }
}

