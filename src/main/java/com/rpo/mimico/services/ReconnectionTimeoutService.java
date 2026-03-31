package com.rpo.mimico.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Scheduled service to auto-forfeit matches when reconnection timeout expires.
 *
 * Runs every 5 seconds to check for reconnection keys older than RECONNECTION_TIMEOUT_SECONDS.
 * When a player's disconnect time exceeds the timeout, the match is forfeited in favor of
 * the opponent team.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconnectionTimeoutService {

    private final StringRedisTemplate redisTemplate;
    private final ReconnectionService reconnectionService;

    @Scheduled(fixedRate = 5000)
    public void checkExpiredReconnections() {
        Set<String> keys = redisTemplate.keys("reconnection:*");

        if (keys == null || keys.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        for (String key : keys) {
            String[] parts = key.split(":");
            if (parts.length != 3) {
                log.warn("Invalid reconnection key format: {}", key);
                continue;
            }

            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                continue; // already expired and cleaned by Redis TTL
            }

            try {
                LocalDateTime disconnectTime = LocalDateTime.parse(value);
                long elapsedSeconds = Duration.between(disconnectTime, now).getSeconds();

                if (elapsedSeconds >= ReconnectionService.RECONNECTION_TIMEOUT_SECONDS) {
                    UUID matchId = UUID.fromString(parts[1]);
                    UUID userId = UUID.fromString(parts[2]);

                    log.info("Reconnection timeout expired: matchId={}, userId={}, elapsedSeconds={}",
                            matchId, userId, elapsedSeconds);

                    reconnectionService.forfeitMatch(matchId, userId);
                }
            } catch (Exception e) {
                log.error("Failed to process reconnection key {}: {}", key, e.getMessage());
                redisTemplate.delete(key);
            }
        }
    }
}
