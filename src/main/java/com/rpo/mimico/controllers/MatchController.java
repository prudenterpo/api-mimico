package com.rpo.mimico.controllers;

import com.rpo.mimico.dtos.MatchResponseDTO;
import com.rpo.mimico.dtos.StartMatchRequestDTO;
import com.rpo.mimico.services.MatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/matches")
@RequiredArgsConstructor
@Tag(name = "Matches", description = "Match management and gameplay")
@SecurityRequirement(name = "bearer-jwt")
public class MatchController {

    private final MatchService matchService;

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
}
