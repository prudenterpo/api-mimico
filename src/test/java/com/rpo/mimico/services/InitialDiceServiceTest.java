package com.rpo.mimico.services;

import com.rpo.mimico.domain.MatchStatus;
import com.rpo.mimico.domain.RoundState;
import com.rpo.mimico.entities.GameTableEntity;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InitialDiceServiceTest {

    @Mock
    private MatchStateRepository matchStateRepository;
    @Mock
    private MatchRepository matchRepository;
    @Mock
    private MatchPlayerRepository matchPlayerRepository;
    @Mock
    private MatchEventPublisher matchEventPublisher;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private final MatchCommandLock lock = new MatchCommandLock() {
        @Override
        public <T> T execute(UUID matchId, Supplier<T> action) {
            return action.get();
        }
    };

    private UUID matchId;
    private UserEntity host;
    private UserEntity playerA;
    private UserEntity playerB;
    private MatchEntity match;
    private MatchStateEntity state;
    private InitialDiceService service;

    @BeforeEach
    void setUp() {
        matchId = UUID.randomUUID();
        host = user("host");
        playerA = user("a");
        playerB = user("b");

        GameTableEntity table = new GameTableEntity();
        table.setId(UUID.randomUUID());
        table.setHost(host);

        match = new MatchEntity();
        match.setId(matchId);
        match.setTable(table);
        match.setMatchStatus(MatchStatus.MATCH_SETUP);

        state = new MatchStateEntity();
        state.setMatch(match);
        state.setTeamAPosition(0);
        state.setTeamBPosition(0);
        state.setIsPaused(false);

        MatchPlayerEntity matchPlayerA = player(playerA, 'A');
        MatchPlayerEntity matchPlayerB = player(playerB, 'B');

        when(matchStateRepository.findByMatchId(matchId)).thenReturn(Optional.of(state));
        when(matchStateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchPlayerRepository.findByMatchIdAndUserId(matchId, playerA.getId())).thenReturn(Optional.of(matchPlayerA));
        when(matchPlayerRepository.findByMatchIdAndUserId(matchId, playerB.getId())).thenReturn(Optional.of(matchPlayerB));

        service = new InitialDiceService(
                matchStateRepository,
                matchRepository,
                matchPlayerRepository,
                new DeterministicDiceService(4, 4, 6, 2),
                lock,
                matchEventPublisher,
                messagingTemplate
        );
    }

    @Test
    void tieRequiresRerollAndWinnerStartsMatch() {
        service.selectPlayers(matchId, host.getId(), playerA.getId(), playerB.getId());
        service.roll(matchId, playerA.getId());
        service.roll(matchId, playerB.getId());

        assertNull(state.getSorteioRollA());
        assertNull(state.getSorteioRollB());
        assertEquals(MatchStatus.MATCH_SETUP, match.getMatchStatus());
        verify(matchEventPublisher, never()).publishState(any());

        service.roll(matchId, playerA.getId());
        service.roll(matchId, playerB.getId());

        assertEquals('A', state.getCurrentTeam());
        assertEquals(playerA.getId(), state.getCurrentMimePlayer().getId());
        assertEquals(MatchStatus.MATCH_ACTIVE, match.getMatchStatus());
        assertEquals(RoundState.ROUND_WAITING_FOR_DICE, state.getRoundState());
        assertEquals(6, state.getSorteioRollA());
        assertEquals(2, state.getSorteioRollB());
        verify(matchEventPublisher).publishState(matchId);
    }

    @Test
    void onlyHostCanSelectOnePlayerFromEachTeam() {
        assertThrows(IllegalArgumentException.class,
                () -> service.selectPlayers(matchId, playerA.getId(), playerA.getId(), playerB.getId()));
        assertThrows(IllegalArgumentException.class,
                () -> service.selectPlayers(matchId, host.getId(), playerB.getId(), playerA.getId()));
    }

    private UserEntity user(String nickname) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setNickname(nickname);
        return user;
    }

    private MatchPlayerEntity player(UserEntity user, char team) {
        MatchPlayerEntity player = new MatchPlayerEntity();
        player.setUser(user);
        player.setTeam(team);
        player.setPlayerOrder(1);
        return player;
    }
}
