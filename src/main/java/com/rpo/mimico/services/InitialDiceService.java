package com.rpo.mimico.services;

import com.rpo.mimico.entities.MatchPlayerEntity;
import com.rpo.mimico.entities.MatchStateEntity;
import com.rpo.mimico.repositories.MatchPlayerRepository;
import com.rpo.mimico.repositories.MatchStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class InitialDiceService {

    private static final String KEY_PLAYER_A = "match:%s:sorteio:playerA";
    private static final String KEY_PLAYER_B = "match:%s:sorteio:playerB";
    private static final String KEY_ROLL_A   = "match:%s:sorteio:rollA";
    private static final String KEY_ROLL_B   = "match:%s:sorteio:rollB";
    private static final long TTL_SECONDS = 600;

    private final MatchStateRepository matchStateRepository;
    private final MatchPlayerRepository matchPlayerRepository;
    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    private final Random random = new Random();

    /*
     * Host selects one player from each team to roll the dice.
     * UC08: host escolhe 1 jogador de cada equipe.
     */
    @Transactional
    public void selectPlayers(UUID matchId, UUID hostId, UUID playerAId, UUID playerBId) {
        MatchStateEntity matchState = getMatchState(matchId);

        if (matchState.getCurrentTeam() != null) {
            throw new IllegalStateException("Sorteio already completed for this match");
        }

        UUID tableHostId = matchState.getMatch().getTable().getHost().getId();
        if (!tableHostId.equals(hostId)) {
            throw new IllegalArgumentException("Only the table host can select sorteio players");
        }

        MatchPlayerEntity mpA = matchPlayerRepository.findByMatchIdAndUserId(matchId, playerAId)
                .orElseThrow(() -> new IllegalArgumentException("Player A not found in match: " + playerAId));
        if (mpA.getTeam() != 'A') {
            throw new IllegalArgumentException("Player A must be from team A");
        }

        MatchPlayerEntity mpB = matchPlayerRepository.findByMatchIdAndUserId(matchId, playerBId)
                .orElseThrow(() -> new IllegalArgumentException("Player B not found in match: " + playerBId));
        if (mpB.getTeam() != 'B') {
            throw new IllegalArgumentException("Player B must be from team B");
        }

        redisTemplate.opsForValue().set(key(KEY_PLAYER_A, matchId), playerAId.toString(), TTL_SECONDS, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(key(KEY_PLAYER_B, matchId), playerBId.toString(), TTL_SECONDS, TimeUnit.SECONDS);
        redisTemplate.delete(key(KEY_ROLL_A, matchId));
        redisTemplate.delete(key(KEY_ROLL_B, matchId));

        log.info("Sorteio players selected: match={}, playerA={}, playerB={}", matchId, playerAId, playerBId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "SORTEIO_PLAYERS_SELECTED");
        payload.put("playerAId", playerAId.toString());
        payload.put("playerANickname", mpA.getUser().getNickname());
        payload.put("playerBId", playerBId.toString());
        payload.put("playerBNickname", mpB.getUser().getNickname());
        messagingTemplate.convertAndSend("/topic/match/" + matchId + "/sorteio", payload);
    }

    /*
     * A selected player rolls the dice for the sorteio.
     * After both have rolled, result is resolved automatically.
     * Ties reset rolls so both players must roll again.
     */
    @Transactional
    public void roll(UUID matchId, UUID playerId) {
        MatchStateEntity matchState = getMatchState(matchId);

        if (matchState.getCurrentTeam() != null) {
            throw new IllegalStateException("Sorteio already completed");
        }

        String storedA = redisTemplate.opsForValue().get(key(KEY_PLAYER_A, matchId));
        String storedB = redisTemplate.opsForValue().get(key(KEY_PLAYER_B, matchId));

        if (storedA == null || storedB == null) {
            throw new IllegalStateException("Host must select players before rolling");
        }

        UUID playerAId = UUID.fromString(storedA);
        UUID playerBId = UUID.fromString(storedB);

        boolean isPlayerA = playerId.equals(playerAId);
        boolean isPlayerB = playerId.equals(playerBId);

        if (!isPlayerA && !isPlayerB) {
            throw new IllegalArgumentException("Player is not selected for sorteio: " + playerId);
        }

        String rollKey = isPlayerA ? key(KEY_ROLL_A, matchId) : key(KEY_ROLL_B, matchId);

        if (redisTemplate.opsForValue().get(rollKey) != null) {
            throw new IllegalStateException("Player already rolled for sorteio");
        }

        int rollValue = random.nextInt(6) + 1;
        redisTemplate.opsForValue().set(rollKey, String.valueOf(rollValue), TTL_SECONDS, TimeUnit.SECONDS);

        Character team = isPlayerA ? 'A' : 'B';
        log.info("Sorteio roll: match={}, player={}, team={}, value={}", matchId, playerId, team, rollValue);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "SORTEIO_ROLL");
        payload.put("playerId", playerId.toString());
        payload.put("team", team.toString());
        payload.put("value", rollValue);
        messagingTemplate.convertAndSend("/topic/match/" + matchId + "/sorteio", payload);

        String rollAStr = redisTemplate.opsForValue().get(key(KEY_ROLL_A, matchId));
        String rollBStr = redisTemplate.opsForValue().get(key(KEY_ROLL_B, matchId));

        if (rollAStr != null && rollBStr != null) {
            resolveSorteio(matchId, matchState, playerAId, playerBId,
                    Integer.parseInt(rollAStr), Integer.parseInt(rollBStr));
        }
    }

    private void resolveSorteio(UUID matchId, MatchStateEntity matchState,
                                UUID playerAId, UUID playerBId, int rollA, int rollB) {
        if (rollA == rollB) {
            redisTemplate.delete(key(KEY_ROLL_A, matchId));
            redisTemplate.delete(key(KEY_ROLL_B, matchId));

            log.info("Sorteio tie: match={}, rollA={}, rollB={} — re-roll required", matchId, rollA, rollB);

            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "SORTEIO_TIE");
            payload.put("playerAId", playerAId.toString());
            payload.put("playerBId", playerBId.toString());
            payload.put("rollA", rollA);
            payload.put("rollB", rollB);
            messagingTemplate.convertAndSend("/topic/match/" + matchId + "/sorteio", payload);
            return;
        }

        Character winnerTeam = rollA > rollB ? 'A' : 'B';
        UUID winnerPlayerId = rollA > rollB ? playerAId : playerBId;

        MatchPlayerEntity winnerPlayer = matchPlayerRepository.findByMatchIdAndUserId(matchId, winnerPlayerId)
                .orElseThrow(() -> new IllegalStateException("Winner player not found in match"));

        matchState.setCurrentTeam(winnerTeam);
        matchState.setCurrentMimePlayer(winnerPlayer.getUser());
        matchStateRepository.save(matchState);

        redisTemplate.delete(key(KEY_PLAYER_A, matchId));
        redisTemplate.delete(key(KEY_PLAYER_B, matchId));
        redisTemplate.delete(key(KEY_ROLL_A, matchId));
        redisTemplate.delete(key(KEY_ROLL_B, matchId));

        log.info("Sorteio complete: match={}, winnerTeam={}, winnerPlayer={}, rollA={}, rollB={}",
                matchId, winnerTeam, winnerPlayerId, rollA, rollB);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "SORTEIO_COMPLETE");
        payload.put("winnerTeam", winnerTeam.toString());
        payload.put("winnerPlayerId", winnerPlayerId.toString());
        payload.put("rollA", rollA);
        payload.put("rollB", rollB);
        messagingTemplate.convertAndSend("/topic/match/" + matchId + "/sorteio", payload);
    }

    private MatchStateEntity getMatchState(UUID matchId) {
        return matchStateRepository.findByMatchId(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match state not found: " + matchId));
    }

    private String key(String template, UUID matchId) {
        return String.format(template, matchId);
    }
}
