package com.rpo.mimico.services;

import com.rpo.mimico.domain.FinishReason;
import com.rpo.mimico.domain.MatchStatus;
import com.rpo.mimico.domain.PauseReason;
import com.rpo.mimico.domain.RoundResolution;
import com.rpo.mimico.domain.RoundState;
import com.rpo.mimico.dtos.WordCardResponseDTO;
import com.rpo.mimico.entities.GameRoundEntity;
import com.rpo.mimico.entities.GameTableEntity;
import com.rpo.mimico.entities.MatchEntity;
import com.rpo.mimico.entities.MatchPlayerEntity;
import com.rpo.mimico.entities.MatchStateEntity;
import com.rpo.mimico.entities.RoundWordCardEntity;
import com.rpo.mimico.entities.UserEntity;
import com.rpo.mimico.entities.WordEntity;
import com.rpo.mimico.repositories.GameRoundRepository;
import com.rpo.mimico.repositories.GameTableRepository;
import com.rpo.mimico.repositories.MatchPlayerRepository;
import com.rpo.mimico.repositories.MatchRepository;
import com.rpo.mimico.repositories.MatchStateRepository;
import com.rpo.mimico.repositories.RoundWordCardRepository;
import com.rpo.mimico.repositories.WordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameplayServiceTest {

    @Mock
    private MatchStateRepository matchStateRepository;
    @Mock
    private MatchRepository matchRepository;
    @Mock
    private MatchPlayerRepository matchPlayerRepository;
    @Mock
    private WordRepository wordRepository;
    @Mock
    private GameRoundRepository gameRoundRepository;
    @Mock
    private RoundWordCardRepository roundWordCardRepository;
    @Mock
    private GameTableRepository gameTableRepository;
    @Mock
    private MatchEventPublisher matchEventPublisher;
    @Mock
    private StringRedisTemplate redisTemplate;

    private final MatchCommandLock lock = new MatchCommandLock() {
        @Override
        public <T> T execute(UUID matchId, Supplier<T> action) {
            return action.get();
        }
    };
    private final WordSelector wordSelector = candidates -> candidates.get(0);
    private final ControllableTimerService timer = new ControllableTimerService(Instant.parse("2026-09-26T12:00:00Z"));
    private final AtomicReference<GameRoundEntity> roundRef = new AtomicReference<>();
    private final List<RoundWordCardEntity> cards = new ArrayList<>();

    private UUID matchId;
    private UserEntity mime;
    private UserEntity partner;
    private UserEntity opponent;
    private UserEntity opponentTwo;
    private MatchPlayerEntity mimePlayer;
    private MatchPlayerEntity partnerPlayer;
    private MatchPlayerEntity opponentPlayer;
    private MatchPlayerEntity opponentTwoPlayer;
    private MatchEntity match;
    private MatchStateEntity state;
    private WordEntity euSou;
    private WordEntity euFaco;
    private WordEntity objeto;
    private GameplayService service;

    @BeforeEach
    void setUp() {
        matchId = UUID.randomUUID();
        mime = user("mime");
        partner = user("partner");
        opponent = user("opponent");
        opponentTwo = user("opponent-2");
        mimePlayer = player(mime, 'A', 1);
        partnerPlayer = player(partner, 'A', 2);
        opponentPlayer = player(opponent, 'B', 1);
        opponentTwoPlayer = player(opponentTwo, 'B', 2);

        GameTableEntity table = new GameTableEntity();
        table.setId(UUID.randomUUID());
        table.setStatus(GameTableEntity.TableStatus.TABLE_IN_MATCH);

        match = new MatchEntity();
        match.setId(matchId);
        match.setTable(table);
        match.setMatchStatus(MatchStatus.MATCH_ACTIVE);

        state = new MatchStateEntity();
        state.setMatch(match);
        state.setTeamAPosition(0);
        state.setTeamBPosition(0);
        state.setCurrentTeam('A');
        state.setCurrentMimePlayer(mime);
        state.setRoundState(RoundState.ROUND_WAITING_FOR_DICE);
        state.setIsPaused(false);

        euSou = word("gato");
        euFaco = word("pular");
        objeto = word("livro");

        when(matchStateRepository.findByMatchId(matchId)).thenReturn(Optional.of(state));
        when(matchStateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(gameTableRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchPlayerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchPlayerRepository.findByMatchIdAndUserId(matchId, mime.getId())).thenReturn(Optional.of(mimePlayer));
        when(matchPlayerRepository.findByMatchIdAndUserId(matchId, partner.getId())).thenReturn(Optional.of(partnerPlayer));
        when(matchPlayerRepository.findByMatchIdAndUserId(matchId, opponent.getId())).thenReturn(Optional.of(opponentPlayer));
        when(matchPlayerRepository.findByMatchIdAndTeam(matchId, 'A')).thenReturn(List.of(mimePlayer, partnerPlayer));
        when(matchPlayerRepository.findByMatchIdAndTeam(matchId, 'B')).thenReturn(List.of(opponentPlayer, opponentTwoPlayer));
        when(gameRoundRepository.save(any())).thenAnswer(invocation -> {
            GameRoundEntity round = invocation.getArgument(0);
            if (round.getId() == null) {
                round.setId(UUID.randomUUID());
            }
            roundRef.set(round);
            return round;
        });
        when(gameRoundRepository.findFirstByMatch_IdOrderByRoundNumberDesc(matchId))
                .thenAnswer(invocation -> Optional.ofNullable(roundRef.get()));
        when(roundWordCardRepository.save(any())).thenAnswer(invocation -> {
            cards.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(roundWordCardRepository.findByRound_IdOrderBySelectionOrderAsc(any())).thenAnswer(invocation -> List.copyOf(cards));
        when(wordRepository.findByCategoryName("eu_sou")).thenReturn(List.of(euSou));
        when(wordRepository.findByCategoryName("eu_faco")).thenReturn(List.of(euFaco));
        when(wordRepository.findByCategoryName("objeto")).thenReturn(List.of(objeto));

        service = serviceWith(new DeterministicDiceService(3, 6, 4));
    }

    @Test
    void rollDiceAdvancesCurrentTeamAndWaitsForWordSelection() {
        service.rollDice(matchId, partner.getId());

        assertEquals(3, state.getTeamAPosition());
        assertEquals(0, state.getTeamBPosition());
        assertEquals(RoundState.ROUND_WAITING_FOR_WORD_SELECTION, state.getRoundState());
        assertEquals(Boolean.FALSE, roundRef.get().getSpecialTile());
        verify(matchEventPublisher).publishState(matchId);
        verify(matchEventPublisher, never()).publishEnded(any());
    }

    @Test
    void gameplayCommandsAreRejectedWhileMimeMediaFailed() {
        state.setIsPaused(true);
        state.setPauseReason(PauseReason.MIME_MEDIA_FAILED);
        match.setMatchStatus(MatchStatus.MATCH_PAUSED);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.rollDice(matchId, partner.getId())
        );

        assertEquals("match is paused", error.getMessage());
        verify(matchRepository, never()).save(any());
    }

    @Test
    void rollDiceCannotHappenDuringGuessing() {
        state.setRoundState(RoundState.ROUND_GUESSING);
        GameplayService guarded = serviceWith(new DiceService() {
            @Override
            public int roll() {
                throw new AssertionError("dice should not roll");
            }
        });

        assertThrows(IllegalStateException.class, () -> guarded.rollDice(matchId, partner.getId()));
    }

    @Test
    void tile52EndsMatchImmediatelyAndCapsPosition() {
        state.setTeamAPosition(50);
        GameplayService winningRoll = serviceWith(new DeterministicDiceService(6));

        winningRoll.rollDice(matchId, mime.getId());

        assertEquals(52, state.getTeamAPosition());
        assertEquals(MatchStatus.MATCH_FINISHED, match.getMatchStatus());
        assertEquals(FinishReason.BOARD_WIN, match.getFinishReason());
        assertEquals('A', match.getWinnerTeam());
        assertEquals(RoundResolution.MATCH_FINISHED, roundRef.get().getResolution());
        assertEquals(GameTableEntity.TableStatus.TABLE_BETWEEN_MATCHES, match.getTable().getStatus());
        verify(matchEventPublisher).publishEnded(match);
    }

    @Test
    void finishedMatchRejectsGameplayCommands() {
        match.setMatchStatus(MatchStatus.MATCH_FINISHED);

        assertThrows(IllegalStateException.class, () -> service.rollDice(matchId, mime.getId()));
    }

    @Test
    void opponentCannotRollDice() {
        assertThrows(IllegalArgumentException.class, () -> service.rollDice(matchId, opponent.getId()));
    }

    @Test
    void wordCardHasOneOptionPerCategoryAndIsPrivateToMime() {
        service.rollDice(matchId, mime.getId());

        WordCardResponseDTO card = service.drawWordCard(matchId, mime.getId());

        assertEquals(List.of("EU_SOU", "EU_FACO", "OBJETO"), card.words().stream().map(WordCardResponseDTO.WordOption::category).toList());
        assertEquals(List.of(euSou.getId(), euFaco.getId(), objeto.getId()), card.words().stream().map(WordCardResponseDTO.WordOption::wordId).toList());
        verify(matchEventPublisher).publishWordCard(mime.getId(), matchId, card.words());
    }

    @Test
    void nonMimeCannotDrawWordCard() {
        service.rollDice(matchId, mime.getId());

        assertThrows(IllegalArgumentException.class, () -> service.drawWordCard(matchId, partner.getId()));
    }

    @Test
    void wordSelectionRejectsWordOutsideCardAndStartsSixtySecondTimer() {
        service.rollDice(matchId, mime.getId());
        service.drawWordCard(matchId, mime.getId());

        assertThrows(IllegalArgumentException.class, () -> service.selectWord(matchId, UUID.randomUUID(), mime.getId()));

        service.selectWord(matchId, euFaco.getId(), mime.getId());

        assertEquals(RoundState.ROUND_GUESSING, state.getRoundState());
        assertEquals(GameClock.toLocalDateTime(timer.now()).plusSeconds(60), state.getRoundExpiresAt());
        assertEquals(euFaco, state.getCurrentWord());
    }

    @Test
    void correctSameTeamGuessKeepsTurnAndRotatesMimeByLastMimeRound() {
        prepareGuessingRound(10, 1);
        service.handleCorrectGuess(matchId, partner.getId());

        assertEquals('A', state.getCurrentTeam());
        assertEquals(partner.getId(), state.getCurrentMimePlayer().getId());
        assertEquals(1, mimePlayer.getLastMimeRound());
        assertEquals(RoundState.ROUND_WAITING_FOR_DICE, state.getRoundState());
        assertEquals(RoundResolution.CORRECT_GUESS, roundRef.get().getResolution());

        prepareGuessingRound(10, 2);
        service.handleCorrectGuess(matchId, mime.getId());

        assertEquals('A', state.getCurrentTeam());
        assertEquals(mime.getId(), state.getCurrentMimePlayer().getId());
        assertEquals(2, partnerPlayer.getLastMimeRound());
    }

    @Test
    void correctOpponentGuessOnSpecialTileStealsTurnWithoutRevertingPosition() {
        prepareGuessingRound(11, 1);

        service.handleCorrectGuess(matchId, opponent.getId());

        assertEquals(11, state.getTeamAPosition());
        assertEquals('B', state.getCurrentTeam());
        assertEquals(opponent.getId(), state.getCurrentMimePlayer().getId());
        assertEquals(RoundResolution.STEAL, roundRef.get().getResolution());
    }

    @Test
    void normalTileRejectsOpponentGuessAndMimeCannotGuess() {
        prepareGuessingRound(10, 1);

        assertThrows(IllegalStateException.class, () -> service.handleCorrectGuess(matchId, opponent.getId()));
        assertThrows(IllegalArgumentException.class, () -> service.handleCorrectGuess(matchId, mime.getId()));
    }

    @Test
    void timeoutSwitchesTurn() {
        prepareGuessingRound(8, 1);

        service.handleTimeout(matchId);

        assertEquals('B', state.getCurrentTeam());
        assertEquals(opponent.getId(), state.getCurrentMimePlayer().getId());
        assertEquals(1, mimePlayer.getLastMimeRound());
        assertEquals(RoundResolution.TIMEOUT, roundRef.get().getResolution());
        assertEquals(RoundState.ROUND_WAITING_FOR_DICE, state.getRoundState());
        verify(matchEventPublisher).publishState(matchId);
    }

    private void prepareGuessingRound(int position, int roundNumber) {
        state.setTeamAPosition(position);
        state.setCurrentTeam('A');
        state.setRoundState(RoundState.ROUND_GUESSING);
        state.setCurrentWord(euSou);
        state.setRoundExpiresAt(GameClock.toLocalDateTime(timer.now()).plusSeconds(30));
        GameRoundEntity round = GameRoundEntity.builder()
                .match(match)
                .roundNumber(roundNumber)
                .roundState(RoundState.ROUND_GUESSING)
                .currentTeam('A')
                .mimePlayer(state.getCurrentTeam() == 'A' ? mimePlayer : partnerPlayer)
                .landingTile(position)
                .specialTile(com.rpo.mimico.domain.BoardRules.isSpecial(position))
                .build();
        round.setId(UUID.randomUUID());
        roundRef.set(round);
    }

    private GameplayService serviceWith(DiceService diceService) {
        return new GameplayService(
                matchStateRepository,
                matchRepository,
                matchPlayerRepository,
                wordRepository,
                gameRoundRepository,
                roundWordCardRepository,
                gameTableRepository,
                diceService,
                timer,
                wordSelector,
                lock,
                matchEventPublisher,
                redisTemplate
        );
    }

    private UserEntity user(String nickname) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setNickname(nickname);
        return user;
    }

    private MatchPlayerEntity player(UserEntity user, char team, int order) {
        MatchPlayerEntity player = new MatchPlayerEntity();
        player.setId(UUID.randomUUID());
        player.setUser(user);
        player.setTeam(team);
        player.setPlayerOrder(order);
        return player;
    }

    private WordEntity word(String text) {
        WordEntity word = new WordEntity();
        word.setId(UUID.randomUUID());
        word.setText(text);
        return word;
    }
}
