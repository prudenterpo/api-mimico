package com.rpo.mimico.services;

import com.rpo.mimico.dtos.ChatMessageDTO;
import com.rpo.mimico.dtos.ChatValidationResultDTO;
import com.rpo.mimico.entities.*;
import com.rpo.mimico.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatValidationServiceTest {

    @Mock
    private MatchStateRepository matchStateRepository;
    @Mock
    private MatchPlayerRepository matchPlayerRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private GameplayService gameplayService;

    @InjectMocks
    private ChatValidationService chatValidationService;

    private UUID matchId;
    private UUID mimePlayerId;
    private UUID teammateId;
    private UUID opponentId;
    private MatchStateEntity matchState;
    private UserEntity mimePlayer;
    private UserEntity teammate;
    private UserEntity opponent;
    private WordEntity word;

    @BeforeEach
    void setUp() {
        matchId = UUID.randomUUID();
        mimePlayerId = UUID.randomUUID();
        teammateId = UUID.randomUUID();
        opponentId = UUID.randomUUID();

        mimePlayer = new UserEntity();
        mimePlayer.setId(mimePlayerId);
        mimePlayer.setNickname("Mime Player");

        teammate = new UserEntity();
        teammate.setId(teammateId);
        teammate.setNickname("Teammate");

        opponent = new UserEntity();
        opponent.setId(opponentId);
        opponent.setNickname("Opponent");

        word = new WordEntity();
        word.setText("Cachorro");

        MatchEntity match = new MatchEntity();
        match.setId(matchId);

        matchState = new MatchStateEntity();
        matchState.setMatch(match);
        matchState.setCurrentMimePlayer(mimePlayer);
        matchState.setCurrentTeam('A');
        matchState.setTeamAPosition(10);
        matchState.setTeamBPosition(5);
        matchState.setCurrentWord(word);
        matchState.setRoundExpiresAt(LocalDateTime.now().plusSeconds(30));
        matchState.setIsPaused(false);
    }

    @Test
    void validateGuess_correctWord_normalTile_callsHandleCorrectGuess() {
        ChatMessageDTO chatMessage = new ChatMessageDTO(teammateId, "cachorro");

        when(matchStateRepository.findByMatchId(matchId)).thenReturn(Optional.of(matchState));
        when(userRepository.findById(teammateId)).thenReturn(Optional.of(teammate));
        when(matchPlayerRepository.findByMatchIdOrderByPlayerOrder(matchId))
                .thenReturn(createMatchPlayers());

        ChatValidationResultDTO result = chatValidationService.validateGuess(matchId, chatMessage);

        assertTrue(result.isCorrect());
        assertEquals(teammateId, result.playerId());
        assertEquals("Teammate", result.playerName());
        assertEquals('A', result.guesserTeam());
        verify(gameplayService, times(1)).handleCorrectGuess(matchId, teammateId);
    }

    @Test
    void validateGuess_incorrectWord_doesNotCallHandleCorrectGuess() {
        ChatMessageDTO chatMessage = new ChatMessageDTO(teammateId, "gato");

        when(matchStateRepository.findByMatchId(matchId)).thenReturn(Optional.of(matchState));
        when(userRepository.findById(teammateId)).thenReturn(Optional.of(teammate));
        when(matchPlayerRepository.findByMatchIdOrderByPlayerOrder(matchId))
                .thenReturn(createMatchPlayers());

        ChatValidationResultDTO result = chatValidationService.validateGuess(matchId, chatMessage);

        assertFalse(result.isCorrect());
        verify(gameplayService, never()).handleCorrectGuess(any(), any());
    }

    @Test
    void validateGuess_caseInsensitive_acceptsBothCases() {
        ChatMessageDTO chatMessage = new ChatMessageDTO(teammateId, "CACHORRO");

        when(matchStateRepository.findByMatchId(matchId)).thenReturn(Optional.of(matchState));
        when(userRepository.findById(teammateId)).thenReturn(Optional.of(teammate));
        when(matchPlayerRepository.findByMatchIdOrderByPlayerOrder(matchId))
                .thenReturn(createMatchPlayers());

        ChatValidationResultDTO result = chatValidationService.validateGuess(matchId, chatMessage);

        assertTrue(result.isCorrect());
    }

    @Test
    void validateGuess_ignoresAccents() {
        word.setText("Café");
        ChatMessageDTO chatMessage = new ChatMessageDTO(teammateId, "cafe");

        when(matchStateRepository.findByMatchId(matchId)).thenReturn(Optional.of(matchState));
        when(userRepository.findById(teammateId)).thenReturn(Optional.of(teammate));
        when(matchPlayerRepository.findByMatchIdOrderByPlayerOrder(matchId))
                .thenReturn(createMatchPlayers());

        ChatValidationResultDTO result = chatValidationService.validateGuess(matchId, chatMessage);

        assertTrue(result.isCorrect());
    }

    @Test
    void validateGuess_mimePlayerCannotChat() {
        ChatMessageDTO chatMessage = new ChatMessageDTO(mimePlayerId, "cachorro");

        when(matchStateRepository.findByMatchId(matchId)).thenReturn(Optional.of(matchState));

        assertThrows(IllegalArgumentException.class,
                () -> chatValidationService.validateGuess(matchId, chatMessage));
    }

    @Test
    void validateGuess_opponentCannotChatOnNormalTile() {
        ChatMessageDTO chatMessage = new ChatMessageDTO(opponentId, "cachorro");

        when(matchStateRepository.findByMatchId(matchId)).thenReturn(Optional.of(matchState));

        assertThrows(IllegalArgumentException.class,
                () -> chatValidationService.validateGuess(matchId, chatMessage));
    }

    @Test
    void validateGuess_opponentCanChatOnSpecialTile() {
        matchState.setTeamAPosition(11);
        ChatMessageDTO chatMessage = new ChatMessageDTO(opponentId, "cachorro");

        when(matchStateRepository.findByMatchId(matchId)).thenReturn(Optional.of(matchState));
        when(userRepository.findById(opponentId)).thenReturn(Optional.of(opponent));
        when(matchPlayerRepository.findByMatchIdOrderByPlayerOrder(matchId))
                .thenReturn(createMatchPlayers());

        ChatValidationResultDTO result = chatValidationService.validateGuess(matchId, chatMessage);

        assertTrue(result.isCorrect());
        assertEquals('B', result.guesserTeam());
        verify(gameplayService, times(1)).handleCorrectGuess(matchId, opponentId);
    }

    @Test
    void validateGuess_throwsExceptionWhenRoundExpired() {
        matchState.setRoundExpiresAt(LocalDateTime.now().minusSeconds(10));
        ChatMessageDTO chatMessage = new ChatMessageDTO(teammateId, "cachorro");

        when(matchStateRepository.findByMatchId(matchId)).thenReturn(Optional.of(matchState));
        when(userRepository.findById(teammateId)).thenReturn(Optional.of(teammate));

        assertThrows(IllegalStateException.class,
                () -> chatValidationService.validateGuess(matchId, chatMessage));
    }

    @Test
    void validateGuess_throwsExceptionWhenMatchPaused() {
        matchState.setIsPaused(true);
        ChatMessageDTO chatMessage = new ChatMessageDTO(teammateId, "cachorro");

        when(matchStateRepository.findByMatchId(matchId)).thenReturn(Optional.of(matchState));
        when(userRepository.findById(teammateId)).thenReturn(Optional.of(teammate));

        assertThrows(IllegalStateException.class,
                () -> chatValidationService.validateGuess(matchId, chatMessage));
    }

    @Test
    void canPlayerChat_returnsTrueForTeammateOnNormalTile() {
        when(matchStateRepository.findByMatchId(matchId)).thenReturn(Optional.of(matchState));
        when(matchPlayerRepository.findByMatchIdOrderByPlayerOrder(matchId))
                .thenReturn(createMatchPlayers());

        boolean canChat = chatValidationService.canPlayerChat(matchId, teammateId);

        assertTrue(canChat);
    }

    @Test
    void canPlayerChat_returnsFalseForMimePlayer() {
        when(matchStateRepository.findByMatchId(matchId)).thenReturn(Optional.of(matchState));

        boolean canChat = chatValidationService.canPlayerChat(matchId, mimePlayerId);

        assertFalse(canChat);
    }

    @Test
    void canPlayerChat_returnsFalseForOpponentOnNormalTile() {
        when(matchStateRepository.findByMatchId(matchId)).thenReturn(Optional.of(matchState));
        when(matchPlayerRepository.findByMatchIdOrderByPlayerOrder(matchId))
                .thenReturn(createMatchPlayers());

        boolean canChat = chatValidationService.canPlayerChat(matchId, opponentId);

        assertFalse(canChat);
    }

    @Test
    void canPlayerChat_returnsTrueForOpponentOnSpecialTile() {
        matchState.setTeamAPosition(5);
        when(matchStateRepository.findByMatchId(matchId)).thenReturn(Optional.of(matchState));
        when(matchPlayerRepository.findByMatchIdOrderByPlayerOrder(matchId))
                .thenReturn(createMatchPlayers());

        boolean canChat = chatValidationService.canPlayerChat(matchId, opponentId);

        assertTrue(canChat);
    }

    private List<MatchPlayerEntity> createMatchPlayers() {
        MatchPlayerEntity mp1 = new MatchPlayerEntity();
        mp1.setUser(mimePlayer);
        mp1.setTeam('A');

        MatchPlayerEntity mp2 = new MatchPlayerEntity();
        mp2.setUser(teammate);
        mp2.setTeam('A');

        MatchPlayerEntity mp3 = new MatchPlayerEntity();
        mp3.setUser(opponent);
        mp3.setTeam('B');

        MatchPlayerEntity mp4 = new MatchPlayerEntity();
        UserEntity opponent2 = new UserEntity();
        opponent2.setId(UUID.randomUUID());
        mp4.setUser(opponent2);
        mp4.setTeam('B');

        return List.of(mp1, mp2, mp3, mp4);
    }
}