package com.rpo.mimico.controllers;

import com.rpo.mimico.dtos.CreateTableRequestDTO;
import com.rpo.mimico.dtos.TableResponseDTO;
import com.rpo.mimico.services.TableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/tables")
@RequiredArgsConstructor
@Tag(name = "Tables", description = "Game table management")
@SecurityRequirement(name = "bearer-jwt")
public class TableController {

    private final TableService tableService;

    @Operation(
            summary = "Create game table",
            description = "Creates a new game table. The creator becomes the host.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Table created successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid input"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized")
            }
    )
    @PostMapping
    public ResponseEntity<TableResponseDTO> createTable(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody CreateTableRequestDTO request
    ) {
        TableResponseDTO response = tableService.createTable(UUID.fromString(userId), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Get table details",
            description = "Returns details of a specific game table",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Table found"),
                    @ApiResponse(responseCode = "404", description = "Table not found"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized")
            }
    )
    @GetMapping("/{tableId}")
    public ResponseEntity<TableResponseDTO> getTable(@PathVariable UUID tableId) {
        TableResponseDTO response = tableService.getTable(tableId);
        return ResponseEntity.ok(response);
    }
}