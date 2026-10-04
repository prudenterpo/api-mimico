package com.rpo.mimico.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rpo.mimico.dtos.CreateTableRequestDTO;
import com.rpo.mimico.dtos.TableResponseDTO;
import com.rpo.mimico.exceptions.GlobalExceptionHandler;
import com.rpo.mimico.services.TableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TableControllerTest {

    @Mock
    private TableService tableService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TableController(tableService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void createTableIsMappedAtApiTables() throws Exception {
        UUID hostUserId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        when(tableService.createTable(eq(hostUserId), any(CreateTableRequestDTO.class)))
                .thenReturn(tableResponse(tableId, hostUserId, "Mesa V1"));

        mockMvc.perform(post("/api/tables")
                        .principal(new UsernamePasswordAuthenticationToken(hostUserId.toString(), null))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("name", "Mesa V1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tableId").value(tableId.toString()))
                .andExpect(jsonPath("$.name").value("Mesa V1"))
                .andExpect(jsonPath("$.hostUserId").value(hostUserId.toString()))
                .andExpect(jsonPath("$.status").value("TABLE_WAITING"));

        ArgumentCaptor<CreateTableRequestDTO> requestCaptor = ArgumentCaptor.forClass(CreateTableRequestDTO.class);
        verify(tableService).createTable(eq(hostUserId), requestCaptor.capture());
        assertEquals("Mesa V1", requestCaptor.getValue().name());
    }

    @Test
    void createTableOnLegacyTablesPathIsNotMapped() throws Exception {
        mockMvc.perform(post("/tables")
                        .principal(new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(), null))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("name", "Mesa V1"))))
                .andExpect(status().isNotFound());

        verifyNoInteractions(tableService);
    }

    @Test
    void getTableIsMappedAtApiTables() throws Exception {
        UUID hostUserId = UUID.randomUUID();
        UUID tableId = UUID.randomUUID();
        when(tableService.getTable(tableId, hostUserId)).thenReturn(tableResponse(tableId, hostUserId, "Mesa V1"));

        mockMvc.perform(get("/api/tables/{tableId}", tableId)
                        .principal(new UsernamePasswordAuthenticationToken(hostUserId.toString(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableId").value(tableId.toString()))
                .andExpect(jsonPath("$.status").value("TABLE_WAITING"));
    }

    @Test
    void createTableRejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/tables")
                        .principal(new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(), null))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("name", " "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(tableService);
    }

    private TableResponseDTO tableResponse(UUID tableId, UUID hostUserId, String name) {
        return TableResponseDTO.builder()
                .tableId(tableId)
                .name(name)
                .hostUserId(hostUserId)
                .hostNickname("host_1")
                .status("TABLE_WAITING")
                .players(List.of())
                .teamAssignments(List.of())
                .createdAt(LocalDateTime.of(2026, 10, 4, 12, 0))
                .build();
    }
}
