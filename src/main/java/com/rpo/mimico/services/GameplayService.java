package com.rpo.mimico.services;

import com.rpo.mimico.domain.BoardRules;
import com.rpo.mimico.domain.FinishReason;
import com.rpo.mimico.domain.MatchStatus;
import com.rpo.mimico.domain.RoundResolution;
import com.rpo.mimico.domain.RoundState;
import com.rpo.mimico.domain.WordCategories;
import com.rpo.mimico.dtos.DiceRollResponseDTO;
import com.rpo.mimico.dtos.WordCardResponseDTO;
import com.rpo.mimico.entities.GameRoundEntity;
import com.rpo.mimico.entities.GameTableEntity;
import com.rpo.mimico.entities.MatchEntity;
import com.rpo.mimico.entities.MatchPlayerEntity;
import com.rpo.mimico.entities.MatchStateEntity;
import com.rpo.mimico.entities.RoundWordCardEntity;
import com.rpo.mimico.entities.WordEntity;
import com.rpo.mimico.repositories.GameRoundRepository;
import com.rpo.mimico.repositories.GameTableRepository;
import com.rpo.mimico.repositories.MatchPlayerRepository;
import com.rpo.mimico.repositories.MatchRepository;
import com.rpo.mimico.repositories.MatchStateRepository;
import com.rpo.mimico.repositories.RoundWordCardRepository;
import com.rpo.mimico.repositories.WordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameplayService {

    private static final String CHAT_KEY_TEMPLATE = "match:%s:chat";

    private final MatchStateRepository matchStateRepository;
    private final MatchRepository matchRepository;
    private final MatchPlayerRepository matchPlayerRepository;
    private final WordRepository wordRepository;
    private final GameRoundRepository gameRoundRepository;
    private final RoundWordCardRepository roundWordCardRepository;
    private final GameTableRepository gameTableRepository;
    private final DiceService diceService;
    private final TimerService timerService;
    private final WordSelector wordSelector;
    private final MatchCommandLock matchCommandLock;
    private final MatchEventPublisher matchEventPublisher;
    private final StringRedisTemplate redisTemplate;

    public DiceRollResponseDTO rollDice(UUID matchId, UUID userId) {
        return matchCommandLock.execute(matchId, () -> doRollDice(matchId, userId));
    }

    public WordCardResponseDTO drawWordCard(UUID matchId, UUID userId) {
        return matchCommandLock.execute(matchId, () -> doDrawWordCard(matchId, userId));
    }

    public void selectWord(UUID matchId, UUID wordId, UUID userId) {
        matchCommandLock.execute(matchId, () -> {
            doSelectWord(matchId, wordId, userId);
            return null;
        });
    }

    public void handleCorrectGuess(UUID matchId, UUID guesserId) {
        matchCommandLock.execute(matchId, () -> {
            doHandleCorrectGuess(matchId, guesserId);
            return null;
        });
    }

    public void handleTimeout(UUID matchId) {
        matchCommandLock.execute(matchId, () -> {
            doHandleTimeout(matchId);
            return null;
        });
    }

    private DiceRollResponseDTO doRollDice(UUID matchId, UUID userId) {
        MatchStateEntity matchState = getMatchState(matchId);
        MatchEntity match = matchState.getMatch();
        assertPlayable(match, matchState);

        if (matchState.getRoundState() != RoundState.ROUND_WAITING_FOR_DICE) {
            throw new IllegalStateException("round state does not allow command");
        }

        MatchPlayerEntity actor = requirePlayer(matchId, userId);
        if (!actor.getTeam().equals(matchState.getCurrentTeam())) {
            throw new IllegalArgumentException("actor is not eligible");
        }

        int diceValue = diceService.roll();
        int landingTile = advancePosition(matchState, diceValue);
        GameRoundEntity round = createRound(match, matchState, diceValue, landingTile);

        log.info("Dice rolled: match={}, team={}, value={}, landing={}", matchId, matchState.getCurrentTeam(), diceValue, landingTile);

        if (landingTile == BoardRules.BOARD_END) {
            resolveRound(round, RoundResolution.MATCH_FINISHED, now());
            matchState.setRoundState(RoundState.ROUND_RESOLVED);
            finishMatch(matchState, matchState.getCurrentTeam(), FinishReason.BOARD_WIN);
            return DiceRollResponseDTO.builder().value(diceValue).team(matchState.getCurrentTeam()).build();
        }

        round.setRoundState(RoundState.ROUND_WAITING_FOR_WORD_SELECTION);
        gameRoundRepository.save(round);
        matchState.setRoundState(RoundState.ROUND_WAITING_FOR_WORD_SELECTION);
        matchStateRepository.save(matchState);
        matchEventPublisher.publishState(matchId);

        return DiceRollResponseDTO.builder().value(diceValue).team(matchState.getCurrentTeam()).build();
    }

    private WordCardResponseDTO doDrawWordCard(UUID matchId, UUID userId) {
        MatchStateEntity matchState = getMatchState(matchId);
        assertPlayable(matchState.getMatch(), matchState);

        if (matchState.getRoundState() != RoundState.ROUND_WAITING_FOR_WORD_SELECTION) {
            throw new IllegalStateException("round state does not allow command");
        }
        requireMime(matchState, userId);

        GameRoundEntity round = currentRound(matchId);
        List<RoundWordCardEntity> existing = roundWordCardRepository.findByRound_IdOrderBySelectionOrderAsc(round.getId());
        List<WordCardResponseDTO.WordOption> options = existing.isEmpty()
                ? drawFreshCard(round)
                : toOptions(existing);

        matchEventPublisher.publishWordCard(userId, matchId, options);
        log.info("Word card delivered privately: match={}, mimePlayer={}, wordCount={}", matchId, userId, options.size());

        return WordCardResponseDTO.builder().matchId(matchId).words(options).build();
    }

    private void doSelectWord(UUID matchId, UUID wordId, UUID userId) {
        MatchStateEntity matchState = getMatchState(matchId);
        assertPlayable(matchState.getMatch(), matchState);

        if (matchState.getRoundState() != RoundState.ROUND_WAITING_FOR_WORD_SELECTION) {
            throw new IllegalStateException("round state does not allow command");
        }
        requireMime(matchState, userId);

        GameRoundEntity round = currentRound(matchId);
        List<RoundWordCardEntity> card = roundWordCardRepository.findByRound_IdOrderBySelectionOrderAsc(round.getId());
        if (card.isEmpty()) {
            throw new IllegalStateException("word card expired");
        }
        RoundWordCardEntity selected = card.stream()
                .filter(entry -> entry.getWord().getId().equals(wordId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("selected word not in word card"));

        LocalDateTime startedAt = now();
        LocalDateTime expiresAt = startedAt.plusSeconds(BoardRules.ROUND_SECONDS);
        round.setSelectedWord(selected.getWord());
        round.setRoundState(RoundState.ROUND_GUESSING);
        round.setStartedAt(startedAt);
        round.setExpiresAt(expiresAt);
        gameRoundRepository.save(round);

        matchState.setCurrentWord(selected.getWord());
        matchState.setRoundExpiresAt(expiresAt);
        matchState.setRoundState(RoundState.ROUND_GUESSING);
        matchState.setIsPaused(false);
        matchStateRepository.save(matchState);

        log.info("Word selected: match={}, wordId={}, expiresAt={}", matchId, wordId, expiresAt);
        matchEventPublisher.publishState(matchId);
    }

    private void doHandleCorrectGuess(UUID matchId, UUID guesserId) {
        MatchStateEntity matchState = getMatchState(matchId);
        MatchEntity match = matchState.getMatch();
        if (match.getMatchStatus() == MatchStatus.MATCH_FINISHED || match.getFinishedAt() != null) {
            throw new IllegalStateException("match already finished");
        }
        if (Boolean.TRUE.equals(matchState.getIsPaused()) || match.getMatchStatus() == MatchStatus.MATCH_PAUSED) {
            throw new IllegalStateException("match is paused");
        }
        if (matchState.getRoundState() != RoundState.ROUND_GUESSING) {
            throw new IllegalStateException("round state does not allow command");
        }

        MatchPlayerEntity guesser = requirePlayer(matchId, guesserId);
        if (matchState.getCurrentMimePlayer() != null
                && matchState.getCurrentMimePlayer().getId().equals(guesserId)) {
            throw new IllegalArgumentException("actor is not eligible guesser");
        }
        int landingTile = currentPosition(matchState);
        boolean specialTile = BoardRules.isSpecial(landingTile);
        boolean sameTeam = guesser.getTeam().equals(matchState.getCurrentTeam());

        RoundResolution resolution;
        if (sameTeam) {
            resolution = RoundResolution.CORRECT_GUESS;
        } else if (specialTile) {
            resolution = RoundResolution.STEAL;
        } else {
            throw new IllegalStateException("actor is not eligible guesser");
        }

        GameRoundEntity round = currentRound(matchId);
        resolveRound(round, resolution, now());
        clearRoundState(matchState);

        if (landingTile >= BoardRules.BOARD_END) {
            finishMatch(matchState, matchState.getCurrentTeam(), FinishReason.BOARD_WIN);
            return;
        }

        rememberMime(matchId, matchState, round.getRoundNumber());
        if (resolution == RoundResolution.STEAL) {
            switchTurn(matchState);
        } else {
            rotateMime(matchState);
        }
        clearChatMessages(matchId);
        matchStateRepository.save(matchState);
        log.info("Round resolved: match={}, resolution={}, nextTeam={}", matchId, resolution, matchState.getCurrentTeam());
        matchEventPublisher.publishState(matchId);
    }

    private void doHandleTimeout(UUID matchId) {
        MatchStateEntity matchState = getMatchState(matchId);
        MatchEntity match = matchState.getMatch();
        if (match.getFinishedAt() != null || match.getMatchStatus() == MatchStatus.MATCH_FINISHED) {
            return;
        }
        if (Boolean.TRUE.equals(matchState.getIsPaused()) || match.getMatchStatus() == MatchStatus.MATCH_PAUSED) {
            return;
        }
        if (matchState.getRoundState() != RoundState.ROUND_GUESSING) {
            return;
        }

        GameRoundEntity round = currentRound(matchId);
        resolveRound(round, RoundResolution.TIMEOUT, now());
        rememberMime(matchId, matchState, round.getRoundNumber());
        clearRoundState(matchState);
        switchTurn(matchState);
        clearChatMessages(matchId);
        matchStateRepository.save(matchState);
        log.info("Round timed out: match={}, nextTeam={}", matchId, matchState.getCurrentTeam());
        matchEventPublisher.publishState(matchId);
    }

    private List<WordCardResponseDTO.WordOption> drawFreshCard(GameRoundEntity round) {
        List<WordCardResponseDTO.WordOption> options = new ArrayList<>();
        int order = 1;
        for (String categoryName : WordCategories.DATABASE_NAMES) {
            List<WordEntity> candidates = wordRepository.findByCategoryName(categoryName);
            if (candidates.isEmpty()) {
                throw new IllegalStateException("No words found for category: " + categoryName);
            }
            WordEntity word = wordSelector.pick(candidates);
            String contractCategory = WordCategories.contractName(categoryName);
            roundWordCardRepository.save(RoundWordCardEntity.builder()
                    .round(round)
                    .word(word)
                    .category(contractCategory)
                    .selectionOrder(order)
                    .build());
            options.add(new WordCardResponseDTO.WordOption(word.getId(), word.getText(), contractCategory));
            order++;
        }
        return options;
    }

    private List<WordCardResponseDTO.WordOption> toOptions(List<RoundWordCardEntity> card) {
        return card.stream()
                .map(entry -> new WordCardResponseDTO.WordOption(
                        entry.getWord().getId(),
                        entry.getWord().getText(),
                        entry.getCategory()
                ))
                .toList();
    }

    private GameRoundEntity createRound(MatchEntity match, MatchStateEntity state, int diceValue, int landingTile) {
        int roundNumber = gameRoundRepository.findFirstByMatch_IdOrderByRoundNumberDesc(match.getId())
                .map(existing -> existing.getRoundNumber() + 1)
                .orElse(1);
        MatchPlayerEntity mime = matchPlayerRepository.findByMatchIdAndUserId(match.getId(), state.getCurrentMimePlayer().getId())
                .orElseThrow(() -> new IllegalStateException("Current mime player is not in the match"));
        return gameRoundRepository.save(GameRoundEntity.builder()
                .match(match)
                .roundNumber(roundNumber)
                .roundState(RoundState.ROUND_WAITING_FOR_WORD_SELECTION)
                .currentTeam(state.getCurrentTeam())
                .mimePlayer(mime)
                .diceValue(diceValue)
                .landingTile(landingTile)
                .specialTile(BoardRules.isSpecial(landingTile))
                .build());
    }

    private void resolveRound(GameRoundEntity round, RoundResolution resolution, LocalDateTime resolvedAt) {
        round.setRoundState(RoundState.ROUND_RESOLVED);
        round.setResolution(resolution);
        round.setResolvedAt(resolvedAt);
        gameRoundRepository.save(round);
    }

    private int advancePosition(MatchStateEntity matchState, int spaces) {
        int landingTile;
        if (matchState.getCurrentTeam() == 'A') {
            landingTile = BoardRules.advance(matchState.getTeamAPosition(), spaces);
            matchState.setTeamAPosition(landingTile);
        } else {
            landingTile = BoardRules.advance(matchState.getTeamBPosition(), spaces);
            matchState.setTeamBPosition(landingTile);
        }
        matchStateRepository.save(matchState);
        return landingTile;
    }

    private void rememberMime(UUID matchId, MatchStateEntity matchState, int roundNumber) {
        UUID mimeUserId = matchState.getCurrentMimePlayer().getId();
        List<MatchPlayerEntity> team = matchPlayerRepository.findByMatchIdAndTeam(matchId, matchState.getCurrentTeam());
        team.stream()
                .filter(player -> player.getUser().getId().equals(mimeUserId))
                .findFirst()
                .ifPresent(player -> {
                    player.setLastMimeRound(roundNumber);
                    matchPlayerRepository.save(player);
                });
    }

    private void rotateMime(MatchStateEntity matchState) {
        MatchPlayerEntity next = selectNextMime(matchState.getMatch().getId(), matchState.getCurrentTeam());
        matchState.setCurrentMimePlayer(next.getUser());
    }

    private void switchTurn(MatchStateEntity matchState) {
        Character nextTeam = matchState.getCurrentTeam() == 'A' ? 'B' : 'A';
        matchState.setCurrentTeam(nextTeam);
        MatchPlayerEntity next = selectNextMime(matchState.getMatch().getId(), nextTeam);
        matchState.setCurrentMimePlayer(next.getUser());
    }

    private MatchPlayerEntity selectNextMime(UUID matchId, Character team) {
        List<MatchPlayerEntity> teamPlayers = matchPlayerRepository.findByMatchIdAndTeam(matchId, team);
        return teamPlayers.stream()
                .min(Comparator
                        .comparing((MatchPlayerEntity player) -> player.getLastMimeRound() == null
                                ? Integer.MIN_VALUE
                                : player.getLastMimeRound())
                        .thenComparing(player -> player.getPlayerOrder() == null ? Integer.MAX_VALUE : player.getPlayerOrder()))
                .orElseThrow(() -> new IllegalStateException("No players found for team " + team));
    }

    private void finishMatch(MatchStateEntity matchState, Character winnerTeam, FinishReason reason) {
        MatchEntity match = matchState.getMatch();
        if (match.getFinishedAt() != null || match.getMatchStatus() == MatchStatus.MATCH_FINISHED) {
            return;
        }
        match.setWinnerTeam(winnerTeam);
        match.setFinishReason(reason);
        match.setMatchStatus(MatchStatus.MATCH_FINISHED);
        match.setFinishedAt(now());
        matchRepository.save(match);

        GameTableEntity table = match.getTable();
        table.setStatus(GameTableEntity.TableStatus.TABLE_BETWEEN_MATCHES);
        gameTableRepository.save(table);
        matchStateRepository.save(matchState);

        log.info("Match finished: match={}, winner={}, reason={}", match.getId(), winnerTeam, reason);
        matchEventPublisher.publishState(match.getId());
        matchEventPublisher.publishEnded(match);
    }

    private void assertPlayable(MatchEntity match, MatchStateEntity state) {
        if (match.getMatchStatus() == MatchStatus.MATCH_FINISHED || match.getFinishedAt() != null) {
            throw new IllegalStateException("match already finished");
        }
        if (match.getMatchStatus() == MatchStatus.MATCH_PAUSED || Boolean.TRUE.equals(state.getIsPaused())) {
            throw new IllegalStateException("match is paused");
        }
        if (match.getMatchStatus() != MatchStatus.MATCH_ACTIVE) {
            throw new IllegalStateException("match not active");
        }
    }

    private void requireMime(MatchStateEntity state, UUID userId) {
        if (state.getCurrentMimePlayer() == null || !state.getCurrentMimePlayer().getId().equals(userId)) {
            throw new IllegalArgumentException("actor is not current mime player");
        }
    }

    private MatchPlayerEntity requirePlayer(UUID matchId, UUID userId) {
        return matchPlayerRepository.findByMatchIdAndUserId(matchId, userId)
                .orElseThrow(() -> new IllegalArgumentException("actor is not in match"));
    }

    private GameRoundEntity currentRound(UUID matchId) {
        return gameRoundRepository.findFirstByMatch_IdOrderByRoundNumberDesc(matchId)
                .orElseThrow(() -> new IllegalStateException("round state does not allow command"));
    }

    private void clearRoundState(MatchStateEntity matchState) {
        matchState.setCurrentWord(null);
        matchState.setRoundExpiresAt(null);
        matchState.setIsPaused(false);
        matchState.setRoundState(RoundState.ROUND_WAITING_FOR_DICE);
    }

    private int currentPosition(MatchStateEntity matchState) {
        return matchState.getCurrentTeam() == 'A' ? matchState.getTeamAPosition() : matchState.getTeamBPosition();
    }

    private MatchStateEntity getMatchState(UUID matchId) {
        return matchStateRepository.findByMatchId(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match state not found: " + matchId));
    }

    private LocalDateTime now() {
        return GameClock.toLocalDateTime(timerService.now());
    }

    private void clearChatMessages(UUID matchId) {
        redisTemplate.delete(String.format(CHAT_KEY_TEMPLATE, matchId));
    }
}
