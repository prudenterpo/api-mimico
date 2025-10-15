package com.rpo.mimico.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpo.mimico.dtos.DiceRollResponseDTO;
import com.rpo.mimico.dtos.WordCardResponseDTO;
import com.rpo.mimico.entities.*;
import com.rpo.mimico.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameplayService {

    private static final int ROUND_DURATION_SECONDS = 60;
    private static final int BOARD_SIZE = 52;
    private static final Set<Integer> SPECIAL_TILES = Set.of(5, 11, 17, 23, 29, 35, 40, 44, 48, 51);
    private static final String[] CATEGORY_NAMES = {"eu_sou", "eu_faco", "objeto"};
    private static final String WORD_CARD_KEY_TEMPLATE = "match:%s:card";
    private static final String CHAT_KEY_TEMPLATE = "match:%s:chat";

    private final MatchStateRepository matchStateRepository;
    private final MatchRepository matchRepository;
    private final MatchPlayerRepository matchPlayerRepository;
    private final WordRepository wordRepository;
    private final WordCategoryRepository wordCategoryRepository;
    private final GameTableRepository gameTableRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    @Transactional
    public DiceRollResponseDTO rollDice(UUID matchId) {
        MatchStateEntity matchState = getMatchState(matchId);

        if (matchState.getRoundExpiresAt() != null && LocalDateTime.now().isBefore(matchState.getRoundExpiresAt())) {
            throw new IllegalStateException("Cannot roll dice while round is in progress");
        }

        int diceValue = random.nextInt(6) + 1;

        advancePosition(matchState, diceValue);

        log.info("Dice rolled and position advanced: match={}, team={}, value={}, newPosition={}",
                matchId, matchState.getCurrentTeam(), diceValue, getCurrentPosition(matchState));

        return DiceRollResponseDTO.builder()
                .value(diceValue)
                .team(matchState.getCurrentTeam())
                .build();
    }

    @Transactional(readOnly = true)
    public WordCardResponseDTO drawWordCard(UUID matchId) {
        MatchStateEntity matchState = getMatchState(matchId);

        List<WordCardResponseDTO.WordOption> wordOptions = new ArrayList<>();

        for (String categoryName : CATEGORY_NAMES) {
            WordCategoryEntity category = wordCategoryRepository.findByName(categoryName)
                    .orElseThrow(() -> new IllegalStateException("Category not found: " + categoryName));

            WordEntity word = wordRepository.findRandomByCategoryId(category.getId());

            if (word == null) {
                throw new IllegalStateException("No words found for category: " + categoryName);
            }

            wordOptions.add(new WordCardResponseDTO.WordOption(
                    word.getId(),
                    word.getText(),
                    category.getDisplayName()
            ));
        }

        storeWordCardInRedis(matchId, wordOptions);

        log.info("Word card drawn: match={}, mimePlayer={}, wordCount={}",
                matchId, matchState.getCurrentMimePlayer().getId(), wordOptions.size());

        return WordCardResponseDTO.builder()
                .matchId(matchId)
                .words(wordOptions)
                .build();
    }

    @Transactional
    public void selectWord(UUID matchId, UUID wordId) {
        MatchStateEntity matchState = getMatchState(matchId);

        WordEntity word = wordRepository.findById(wordId)
                .orElseThrow(() -> new IllegalArgumentException("Word not found: " + wordId));

        validateWordInCard(matchId, wordId);

        matchState.setCurrentWord(word);
        matchState.setRoundExpiresAt(LocalDateTime.now().plusSeconds(ROUND_DURATION_SECONDS));
        matchState.setIsPaused(false);

        matchStateRepository.save(matchState);

        log.info("Word selected and timer started: match={}, word='{}', expiresAt={}",
                matchId, word.getText(), matchState.getRoundExpiresAt());
    }

    @Transactional
    public void handleCorrectGuess(UUID matchId, UUID guesserId) {
        MatchStateEntity matchState = getMatchState(matchId);

        Character guesserTeam = getPlayerTeam(matchId, guesserId);
        Character currentTeam = matchState.getCurrentTeam();
        int currentPosition = getCurrentPosition(matchState);
        boolean isSpecialTile = SPECIAL_TILES.contains(currentPosition);

        log.info("Correct guess: match={}, guesser={}, guesserTeam={}, currentTeam={}, position={}, special={}",
                matchId, guesserId, guesserTeam, currentTeam, currentPosition, isSpecialTile);

        clearRoundState(matchState);

        if (guesserTeam.equals(currentTeam)) {
            rotateMimePlayer(matchState);
            log.info("Same team continues: match={}, nextMime={}", matchId, matchState.getCurrentMimePlayer().getId());
        } else if (isSpecialTile) {
            switchTurn(matchState);
            log.info("Turn stolen on special tile: match={}, newTeam={}", matchId, matchState.getCurrentTeam());
        } else {
            throw new IllegalStateException("Opponent guessed on normal tile - this should be prevented by chat permissions");
        }

        clearChatMessages(matchId);

        checkWinCondition(matchState);

        matchStateRepository.save(matchState);
    }

    @Transactional
    public void handleTimeout(UUID matchId) {
        MatchStateEntity matchState = getMatchState(matchId);

        log.info("Round timeout: match={}, team={}", matchId, matchState.getCurrentTeam());

        clearRoundState(matchState);

        switchTurn(matchState);

        clearChatMessages(matchId);

        matchStateRepository.save(matchState);

        log.info("Turn switched after timeout: match={}, newTeam={}, newMime={}",
                matchId, matchState.getCurrentTeam(), matchState.getCurrentMimePlayer().getId());
    }

    private void advancePosition(MatchStateEntity matchState, int spaces) {
        Character currentTeam = matchState.getCurrentTeam();
        int newPosition;

        if (currentTeam == 'A') {
            newPosition = Math.min(matchState.getTeamAPosition() + spaces, BOARD_SIZE);
            matchState.setTeamAPosition(newPosition);
        } else {
            newPosition = Math.min(matchState.getTeamBPosition() + spaces, BOARD_SIZE);
            matchState.setTeamBPosition(newPosition);
        }

        matchStateRepository.save(matchState);

        log.debug("Position advanced: team={}, spaces={}, newPosition={}", currentTeam, spaces, newPosition);

        if (newPosition == BOARD_SIZE) {
            finishMatch(matchState, currentTeam);
        }
    }

    private int getCurrentPosition(MatchStateEntity matchState) {
        return matchState.getCurrentTeam() == 'A'
                ? matchState.getTeamAPosition()
                : matchState.getTeamBPosition();
    }

    private void switchTurn(MatchStateEntity matchState) {
        Character newTeam = matchState.getCurrentTeam() == 'A' ? 'B' : 'A';
        matchState.setCurrentTeam(newTeam);

        List<MatchPlayerEntity> teamPlayers = matchPlayerRepository
                .findByMatchIdAndTeam(matchState.getMatch().getId(), newTeam);

        if (teamPlayers.isEmpty()) {
            throw new IllegalStateException("No players found for team " + newTeam);
        }

        matchState.setCurrentMimePlayer(teamPlayers.get(0).getUser());

        log.debug("Turn switched: newTeam={}, newMime={}", newTeam, matchState.getCurrentMimePlayer().getId());
    }

    private void rotateMimePlayer(MatchStateEntity matchState) {
        UUID currentMimeId = matchState.getCurrentMimePlayer().getId();
        Character currentTeam = matchState.getCurrentTeam();

        List<MatchPlayerEntity> teamPlayers = matchPlayerRepository
                .findByMatchIdAndTeam(matchState.getMatch().getId(), currentTeam);

        if (teamPlayers.size() != 2) {
            throw new IllegalStateException("Team must have exactly 2 players");
        }

        UserEntity nextMime;
        if (teamPlayers.get(0).getUser().getId().equals(currentMimeId)) {
            nextMime = teamPlayers.get(1).getUser();
        } else {
            nextMime = teamPlayers.get(0).getUser();
        }

        matchState.setCurrentMimePlayer(nextMime);

        log.debug("Mime player rotated: team={}, newMime={}", currentTeam, nextMime.getId());
    }

    private Character getPlayerTeam(UUID matchId, UUID playerId) {
        List<MatchPlayerEntity> allPlayers = matchPlayerRepository.findByMatchIdOrderByPlayerOrder(matchId);

        return allPlayers.stream()
                .filter(p -> p.getUser().getId().equals(playerId))
                .map(MatchPlayerEntity::getTeam)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Player not in match: " + playerId));
    }

    private void clearRoundState(MatchStateEntity matchState) {
        matchState.setCurrentWord(null);
        matchState.setRoundExpiresAt(null);
        matchState.setIsPaused(false);
    }

    private void checkWinCondition(MatchStateEntity matchState) {
        if (matchState.getTeamAPosition() >= BOARD_SIZE) {
            finishMatch(matchState, 'A');
        } else if (matchState.getTeamBPosition() >= BOARD_SIZE) {
            finishMatch(matchState, 'B');
        }
    }

    private void finishMatch(MatchStateEntity matchState, Character winnerTeam) {
        MatchEntity match = matchState.getMatch();
        match.setWinnerTeam(winnerTeam);
        match.setFinishedAt(LocalDateTime.now());
        matchRepository.save(match);

        GameTableEntity table = match.getTable();
        table.setStatus(GameTableEntity.TableStatus.FINISHED);
        gameTableRepository.save(table);

        log.info("Match finished: match={}, winner={}, teamAPos={}, teamBPos={}",
                match.getId(), winnerTeam, matchState.getTeamAPosition(), matchState.getTeamBPosition());
    }

    private MatchStateEntity getMatchState(UUID matchId) {
        return matchStateRepository.findByMatchId(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match state not found: " + matchId));
    }

    private void storeWordCardInRedis(UUID matchId, List<WordCardResponseDTO.WordOption> wordOptions) {
        String key = String.format(WORD_CARD_KEY_TEMPLATE, matchId);

        try {
            String json = objectMapper.writeValueAsString(wordOptions);
            redisTemplate.opsForValue().set(key, json, 5, TimeUnit.MINUTES);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize word card to JSON: match={}", matchId, e);
            throw new RuntimeException("Failed to store word card", e);
        }
    }

    private void validateWordInCard(UUID matchId, UUID wordId) {
        String key = String.format(WORD_CARD_KEY_TEMPLATE, matchId);
        String json = redisTemplate.opsForValue().get(key);

        if (json == null) {
            throw new IllegalStateException("Word card not found or expired - please draw a new card");
        }

        try {
            List<Map<String, Object>> wordOptions = objectMapper.readValue(json, List.class);
            boolean wordInCard = wordOptions.stream()
                    .anyMatch(option -> option.get("wordId").toString().equals(wordId.toString()));

            if (!wordInCard) {
                throw new IllegalArgumentException("Selected word was not in the drawn card");
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize word card from Redis: match={}", matchId, e);
            throw new RuntimeException("Failed to validate word card", e);
        }
    }

    private void clearChatMessages(UUID matchId) {
        String key = String.format(CHAT_KEY_TEMPLATE, matchId);
        redisTemplate.delete(key);
        log.debug("Chat messages cleared: match={}", matchId);
    }
}