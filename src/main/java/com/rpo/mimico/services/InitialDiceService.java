package com.rpo.mimico.services;

import com.rpo.mimico.domain.MatchStatus;
import com.rpo.mimico.domain.RoundState;
import com.rpo.mimico.entities.MatchEntity;
import com.rpo.mimico.entities.MatchPlayerEntity;
import com.rpo.mimico.entities.MatchStateEntity;
import com.rpo.mimico.repositories.MatchPlayerRepository;
import com.rpo.mimico.repositories.MatchRepository;
import com.rpo.mimico.repositories.MatchStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InitialDiceService {

    private final MatchStateRepository matchStateRepository;
    private final MatchRepository matchRepository;
    private final MatchPlayerRepository matchPlayerRepository;
    private final DiceService diceService;
    private final MatchCommandLock matchCommandLock;
    private final MatchEventPublisher matchEventPublisher;
    private final SimpMessagingTemplate messagingTemplate;

    public void selectPlayers(UUID matchId, UUID hostId, UUID playerAId, UUID playerBId) {
        matchCommandLock.execute(matchId, () -> {
            doSelectPlayers(matchId, hostId, playerAId, playerBId);
            return null;
        });
    }

    public void roll(UUID matchId, UUID playerId) {
        matchCommandLock.execute(matchId, () -> {
            doRoll(matchId, playerId);
            return null;
        });
    }

    private void doSelectPlayers(UUID matchId, UUID hostId, UUID playerAId, UUID playerBId) {
        MatchStateEntity matchState = getMatchState(matchId);
        MatchEntity match = matchState.getMatch();
        assertSetup(match, matchState);

        UUID tableHostId = match.getTable().getHost().getId();
        if (!tableHostId.equals(hostId)) {
            throw new IllegalArgumentException("actor is not host");
        }

        MatchPlayerEntity playerA = requireTeamPlayer(matchId, playerAId, 'A');
        MatchPlayerEntity playerB = requireTeamPlayer(matchId, playerBId, 'B');

        matchState.setSorteioPlayerA(playerA.getUser());
        matchState.setSorteioPlayerB(playerB.getUser());
        matchState.setSorteioRollA(null);
        matchState.setSorteioRollB(null);
        matchStateRepository.save(matchState);

        log.info("Sorteio players selected: match={}, playerA={}, playerB={}", matchId, playerAId, playerBId);
        broadcast(matchId, payload(
                "SORTEIO_PLAYERS_SELECTED",
                "playerAId", playerAId.toString(),
                "playerANickname", playerA.getUser().getNickname(),
                "playerBId", playerBId.toString(),
                "playerBNickname", playerB.getUser().getNickname()
        ));
    }

    private void doRoll(UUID matchId, UUID playerId) {
        MatchStateEntity matchState = getMatchState(matchId);
        assertSetup(matchState.getMatch(), matchState);

        if (matchState.getSorteioPlayerA() == null || matchState.getSorteioPlayerB() == null) {
            throw new IllegalStateException("Host must select players before rolling");
        }

        boolean isPlayerA = playerId.equals(matchState.getSorteioPlayerA().getId());
        boolean isPlayerB = playerId.equals(matchState.getSorteioPlayerB().getId());
        if (!isPlayerA && !isPlayerB) {
            throw new IllegalArgumentException("Player is not selected for sorteio: " + playerId);
        }
        if (isPlayerA && matchState.getSorteioRollA() != null || isPlayerB && matchState.getSorteioRollB() != null) {
            throw new IllegalStateException("Player already rolled for sorteio");
        }

        int rollValue = diceService.roll();
        if (isPlayerA) {
            matchState.setSorteioRollA(rollValue);
        } else {
            matchState.setSorteioRollB(rollValue);
        }
        matchStateRepository.save(matchState);

        Character team = isPlayerA ? 'A' : 'B';
        log.info("Sorteio roll: match={}, player={}, team={}, value={}", matchId, playerId, team, rollValue);
        broadcast(matchId, payload(
                "SORTEIO_ROLL",
                "playerId", playerId.toString(),
                "team", team.toString(),
                "value", rollValue
        ));

        if (matchState.getSorteioRollA() != null && matchState.getSorteioRollB() != null) {
            resolve(matchId, matchState);
        }
    }

    private void resolve(UUID matchId, MatchStateEntity matchState) {
        int rollA = matchState.getSorteioRollA();
        int rollB = matchState.getSorteioRollB();
        UUID playerAId = matchState.getSorteioPlayerA().getId();
        UUID playerBId = matchState.getSorteioPlayerB().getId();

        if (rollA == rollB) {
            matchState.setSorteioRollA(null);
            matchState.setSorteioRollB(null);
            matchStateRepository.save(matchState);
            log.info("Sorteio tie: match={}, rollA={}, rollB={}", matchId, rollA, rollB);
            broadcast(matchId, payload(
                    "SORTEIO_TIE",
                    "playerAId", playerAId.toString(),
                    "playerBId", playerBId.toString(),
                    "rollA", rollA,
                    "rollB", rollB
            ));
            return;
        }

        Character winnerTeam = rollA > rollB ? 'A' : 'B';
        UUID winnerPlayerId = rollA > rollB ? playerAId : playerBId;
        MatchPlayerEntity winner = matchPlayerRepository.findByMatchIdAndUserId(matchId, winnerPlayerId)
                .orElseThrow(() -> new IllegalStateException("Winner player not found in match"));

        matchState.setCurrentTeam(winnerTeam);
        matchState.setCurrentMimePlayer(winner.getUser());
        matchState.setRoundState(RoundState.ROUND_WAITING_FOR_DICE);
        matchStateRepository.save(matchState);

        MatchEntity match = matchState.getMatch();
        match.setMatchStatus(MatchStatus.MATCH_ACTIVE);
        matchRepository.save(match);

        log.info("Sorteio complete: match={}, winnerTeam={}, winnerPlayer={}, rollA={}, rollB={}",
                matchId, winnerTeam, winnerPlayerId, rollA, rollB);
        broadcast(matchId, payload(
                "SORTEIO_COMPLETE",
                "winnerTeam", winnerTeam.toString(),
                "winnerPlayerId", winnerPlayerId.toString(),
                "rollA", rollA,
                "rollB", rollB
        ));
        matchEventPublisher.publishState(matchId);
    }

    private void assertSetup(MatchEntity match, MatchStateEntity state) {
        if (match.getMatchStatus() == MatchStatus.MATCH_FINISHED || match.getFinishedAt() != null) {
            throw new IllegalStateException("match already finished");
        }
        if (state.getCurrentTeam() != null || match.getMatchStatus() == MatchStatus.MATCH_ACTIVE) {
            throw new IllegalStateException("Sorteio already completed for this match");
        }
        if (match.getMatchStatus() != null && match.getMatchStatus() != MatchStatus.MATCH_SETUP) {
            throw new IllegalStateException("match not active");
        }
    }

    private MatchPlayerEntity requireTeamPlayer(UUID matchId, UUID playerId, char team) {
        MatchPlayerEntity player = matchPlayerRepository.findByMatchIdAndUserId(matchId, playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player " + team + " not found in match: " + playerId));
        if (player.getTeam() == null || player.getTeam() != team) {
            throw new IllegalArgumentException("Player " + team + " must be from team " + team);
        }
        return player;
    }

    private MatchStateEntity getMatchState(UUID matchId) {
        return matchStateRepository.findByMatchId(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match state not found: " + matchId));
    }

    private void broadcast(UUID matchId, Map<String, Object> payload) {
        messagingTemplate.convertAndSend("/topic/match/" + matchId + "/sorteio", payload);
    }

    private Map<String, Object> payload(String type, Object... pairs) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        for (int i = 0; i < pairs.length; i += 2) {
            payload.put((String) pairs[i], pairs[i + 1]);
        }
        return payload;
    }
}
