package com.rpo.mimico.controllers;

import com.rpo.mimico.dtos.OnlineUsersResponseDTO;
import com.rpo.mimico.services.OnlineUsersService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/lobby")
@RequiredArgsConstructor
@Tag(name = "Lobby", description = "Lobby operations - view online users")
@SecurityRequirement(name = "bearer-jwt")
public class LobbyController {

    private final OnlineUsersService onlineUsersService;

    @Operation(
            summary = "Get online users",
            description = "Returns list of all currently online users in the lobby",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Online users retrieved successfully"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized")
            }
    )
    @GetMapping("/online-users")
    public ResponseEntity<OnlineUsersResponseDTO> getOnlineUsers() {
        OnlineUsersResponseDTO response = OnlineUsersResponseDTO.builder()
                .users(onlineUsersService.getOnlineUsers())
                .count(onlineUsersService.getOnlineCount())
                .build();

        return ResponseEntity.ok(response);
    }
}
