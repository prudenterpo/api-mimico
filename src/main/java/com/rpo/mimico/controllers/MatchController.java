package com.rpo.mimico.controllers;

import com.rpo.mimico.dtos.MatchResponseDTO;
import com.rpo.mimico.dtos.StartMatchRequestDTO;
import com.rpo.mimico.services.MatchService;
import com.rpo.mimico.services.ReconnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/matches")
@RequiredArgsConstructor
@Tag(name = "Matches", description = "Match management and gameplay")
@SecurityRequirement(name = "bearer-jwt")
public class MatchController {

    private final MatchService matchService;
    private final ReconnectionService reconnectionService;

    @Operation(
            summary = "Start match",
            description = "Starts a new match with 4 players, assign teams, rolls dice for starting team",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Match started successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid request"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized")
            }
    )
    @PostMapping("/start")
    public ResponseEntity<MatchResponseDTO> startMatch(@Valid @RequestBody StartMatchRequestDTO request) {
        MatchResponseDTO response = matchService.startMatch(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Forfeit paused match",
            description = "Host ends a paused match due to player disconnection. Opponent team wins.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Match forfeited successfully"),
                    @ApiResponse(responseCode = "404", description = "Match not found"),
                    @ApiResponse(responseCode = "400", description = "Match is not paused"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized")
            }
    )
    @PostMapping("/{matchId}/forfeit")
    public ResponseEntity<String> forfeitMatch(@PathVariable UUID matchId) {
        UUID disconnectedPlayerId = reconnectionService.getDisconnectedPlayer(matchId);

        if (disconnectedPlayerId == null) {
            return ResponseEntity.badRequest().body("No disconnected player found for this match");
        }

        reconnectionService.forfeitMatch(matchId, disconnectedPlayerId);

        return ResponseEntity.ok("Match forfeited successfully");
    }
}
