package com.rpo.mimico.controllers;

import com.rpo.mimico.exceptions.GlobalExceptionHandler;
import com.rpo.mimico.exceptions.MatchNotFoundException;
import com.rpo.mimico.services.MatchService;
import com.rpo.mimico.services.ReconnectionService;
import com.rpo.mimico.services.TablePlayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MatchControllerTest {

    @Mock
    private MatchService matchService;
    @Mock
    private ReconnectionService reconnectionService;
    @Mock
    private TablePlayerService tablePlayerService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MatchController(matchService, reconnectionService, tablePlayerService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getMatchByTableReturns404WhenMissing() throws Exception {
        UUID tableId = UUID.randomUUID();
        when(matchService.getActiveMatchByTableId(tableId)).thenThrow(new MatchNotFoundException(tableId));

        mockMvc.perform(get("/api/matches/table/{tableId}", tableId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATCH_NOT_FOUND"));
    }
}
