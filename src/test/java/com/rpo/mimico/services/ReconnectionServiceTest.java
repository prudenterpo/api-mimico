package com.rpo.mimico.services;

import com.rpo.mimico.entities.MatchEntity;
import com.rpo.mimico.entities.MatchPlayerEntity;
import com.rpo.mimico.entities.MatchStateEntity;
import com.rpo.mimico.entities.UserEntity;
import com.rpo.mimico.repositories.MatchPlayerRepository;
import com.rpo.mimico.repositories.MatchRepository;
import com.rpo.mimico.repositories.MatchStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ReconnectionServiceTest {

    @Mock
    private MatchStateRepository matchStateRepository;
    @Mock
    private MatchRepository matchRepository;
    @Mock
    private MatchPlayerRepository matchPlayerRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ReconnectionService reconnectionService;

    private UUID matchId;
    private UUID userId;
    private MatchStateEntity matchState;
    private MatchEntity match;
    private UserEntity user;
    private MatchPlayerEntity matchPlayer;

    @BeforeEach
    void setUp() {
        matchId = UUID.randomUUID();
        userId = UUID.randomUUID();

        user = new UserEntity();
        user.setId(userId);
        user.setNickname("Test PlayerEntity");

        match = new MatchEntity();
        match.setId(matchId);
        match.setFinishedAt(null);

        matchState = new MatchStateEntity();
        matchState.setMatch(match);
        matchState.setIsPaused(false);
        matchState.setCurrentTeam('A');
        matchState.setCurrentMimePlayer(user);
        matchState.setTeamAPosition(0);
        matchState.setTeamBPosition(0);

        matchPlayer = new MatchPlayerEntity();
        matchPlayer.setMatch(match);
        matchPlayer.setUser(user);
        matchPlayer.setTeam('A');
    }

    @Test
    void handleDisconnect_pausesMatchAndStoresInRedis() {
        when(matchPlayerRepository.findActiveMatchesByUserId(userId)).thenReturn(List.of(matchPlayer));
        when(matchStateRepository.findByMatchId(matchId)).thenReturn(Optional.of(matchState));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        reconnectionService.handleDisconnect(userId);

        assertTrue(matchState.getIsPaused());
        verify(matchStateRepository).save(matchState);
        verify(valueOperations).set(
                eq("reconnection:" + matchId + ":" + userId),
                anyString(),
                eq(3600L),
                any()
        );
        verify(messagingTemplate).convertAndSend(
                eq("/topic/match/" + matchId + "/paused"),
                any(Object.class)
        );
    }

    @Test
    void handleDisconnect_doesNothingWhenNoActiveMatch() {
        when(matchPlayerRepository.findActiveMatchesByUserId(userId)).thenReturn(List.of());

        reconnectionService.handleDisconnect(userId);

        verify(matchStateRepository, never()).save(any());
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void handleDisconnect_doesNothingWhenMatchAlreadyPaused() {
        matchState.setIsPaused(true);
        when(matchPlayerRepository.findActiveMatchesByUserId(userId))
                .thenReturn(List.of(matchPlayer));
        when(matchStateRepository.findByMatchId(matchId))
                .thenReturn(Optional.of(matchState));

        reconnectionService.handleDisconnect(userId);

        verify(matchStateRepository, never()).save(any());
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void handledisconnect_doesNothingWhenMatchFinished() {
        match.setFinishedAt(LocalDateTime.now());
        when(matchPlayerRepository.findActiveMatchesByUserId(userId)).thenReturn(List.of(matchPlayer));
        when(matchStateRepository.findByMatchId(matchId)).thenReturn(Optional.of(matchState));

        reconnectionService.handleDisconnect(userId);

        verify(matchStateRepository,never()).save(any());
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void handleReconnect_resumesMatchAndDeletesFromRedis() {
        matchState.setIsPaused(true);
        String reconnectionKey = "reconnection:" + matchId + ":" + userId;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(matchPlayerRepository.findActiveMatchesByUserId(userId)).thenReturn(List.of(matchPlayer));
        when(valueOperations.get(reconnectionKey)).thenReturn(LocalDateTime.now().toString());
        when(matchStateRepository.findByMatchId(matchId)).thenReturn(Optional.of(matchState));

        reconnectionService.handleReconnect(userId);

        assertFalse(matchState.getIsPaused());
        verify(matchStateRepository).save(matchState);
        verify(redisTemplate).delete(reconnectionKey);
        verify(messagingTemplate).convertAndSendToUser(
                eq(userId.toString()),
                eq("/queue/game-state"),
                any()
        );
        verify(messagingTemplate).convertAndSend(
                eq("/topic/match/" + matchId + "/resumed"),
                any(Object.class)
        );
    }

    @Test
    void handleReconnect_doesNothingWhenNoReconnectionData() {
        String reconnectionKey = "reconnection:" + matchId + ":" + userId;

        when(matchPlayerRepository.findActiveMatchesByUserId(userId)).thenReturn(List.of(matchPlayer));
        when(valueOperations.get(reconnectionKey)).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        reconnectionService.handleReconnect(userId);

        verify(matchStateRepository, never()).save(any());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void forfeitMatch_setsWinnerAndFinishesMatch() {
        List<MatchPlayerEntity> players = createFourPlayers();

        when(matchStateRepository.findByMatchId(matchId)).thenReturn(Optional.of(matchState));
        when(matchPlayerRepository.findByMatchIdOrderByPlayerOrder(matchId)).thenReturn(players);

        reconnectionService.forfeitMatch(matchId, userId);

        assertEquals('B', match.getWinnerTeam());
        assertNotNull(match.getFinishedAt());
        verify(matchRepository).save(match);
        verify(redisTemplate).delete("reconnection:" + matchId + ":" + userId);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/match/" + matchId + "/ended"),
                any(Object.class)
        );
    }

    @Test
    void hasPendingReconnection_returnsTrueWhenKeyExists() {
        List<MatchPlayerEntity> players = createFourPlayers();
        when(matchPlayerRepository.findByMatchIdOrderByPlayerOrder(matchId)).thenReturn(players);
        when(redisTemplate.hasKey("reconnection:" + matchId + ":" + userId)).thenReturn(true);

        boolean result = reconnectionService.hasPendingReconnection(matchId);

        assertTrue(result);
    }

    @Test
    void hasPendingReconnection_returnsFalseWhenNoKeyExists() {
        List<MatchPlayerEntity> players = createFourPlayers();
        when(matchPlayerRepository.findByMatchIdOrderByPlayerOrder(matchId)).thenReturn(players);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        boolean result = reconnectionService.hasPendingReconnection(matchId);

        assertFalse(result);
    }

    @Test
    void getDisconnectedPlayer_returnsUserIdWhenKeyExists() {
        List<MatchPlayerEntity> players = createFourPlayers();
        when(matchPlayerRepository.findByMatchIdOrderByPlayerOrder(matchId)).thenReturn(players);
        when(redisTemplate.hasKey("reconnection:" + matchId + ":" + userId)).thenReturn(true);

        UUID result = reconnectionService.getDisconnectedPlayer(matchId);

        assertEquals(userId, result);
    }

    @Test
    void getDisconnectedPlayer_returnsNullWhenNoKeyExists() {
        List<MatchPlayerEntity> players = createFourPlayers();
        when(matchPlayerRepository.findByMatchIdOrderByPlayerOrder(matchId)).thenReturn(players);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        UUID result = reconnectionService.getDisconnectedPlayer(matchId);

        assertNull(result);
    }
    private List<MatchPlayerEntity> createFourPlayers() {
        MatchPlayerEntity mp1 = new MatchPlayerEntity();
        mp1.setUser(user);
        mp1.setTeam('A');

        MatchPlayerEntity mp2 = new MatchPlayerEntity();
        UserEntity user2 = new UserEntity();
        user2.setId(UUID.randomUUID());
        mp2.setUser(user2);
        mp2.setTeam('A');

        MatchPlayerEntity mp3 = new MatchPlayerEntity();
        UserEntity user3 = new UserEntity();
        user3.setId(UUID.randomUUID());
        mp3.setUser(user3);
        mp3.setTeam('B');

        MatchPlayerEntity mp4 = new MatchPlayerEntity();
        UserEntity user4 = new UserEntity();
        user4.setId(UUID.randomUUID());
        mp4.setUser(user4);
        mp4.setTeam('B');

        return List.of(mp1, mp2, mp3, mp4);
    }
}



























