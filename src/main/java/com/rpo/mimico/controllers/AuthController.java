package com.rpo.mimico.controllers;

import com.rpo.mimico.dtos.LoginRequestDTO;
import com.rpo.mimico.dtos.LoginResponseDTO;
import com.rpo.mimico.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Handles user login")
public class AuthController {

  private final AuthService authService;

  @Operation(
      summary = "User login",
      description = "Authenticates user and returns a JWT token.",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              required = true,
              content =
                  @Content(
                      mediaType = "application/json",
                      examples =
                          @ExampleObject(
                              name = "Login example",
                              value =
                                  """
                        {
                          "email": "user@email.com",
                          "password": "123456"
                        }
                    """))),
      responses = {
        @ApiResponse(responseCode = "200", description = "Login successful"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials")
      })
  @PostMapping("/login")
  public ResponseEntity<LoginResponseDTO> login(@RequestBody LoginRequestDTO request) {
    return ResponseEntity.ok(authService.login(request));
  }
}
