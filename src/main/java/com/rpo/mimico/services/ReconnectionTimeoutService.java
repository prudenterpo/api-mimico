package com.rpo.mimico.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * Scheduled service to handle reconnection timeouts.
 *
 * Runs every 5 minutes to check for expired reconnections.
 * Redis keys have 1-hour TTL, so expired keys are automatically cleaned.
 * This service just provides additional forfeit logic if needed.
 *
 * Note: Most cleanup is automatic via Redis TTL.
 * This service is mainly for logging and ensuring consistent state.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconnectionTimeoutService {

    private final StringRedisTemplate redisTemplate;
    private final ReconnectionService reconnectionService;

    /**
     * Checks for expired reconnections every 5 minutes.
     *
     * Redis automatically expires keys after 1 hour (TTL).
     * This task provides additional monitoring and logging.
     *
     * If a reconnection key exists beyond expected time, it means:
     * - Player never reconnected
     * - Host never manually ended the match
     * - System should auto-forfeit
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void checkExpiredReconnections() {
        try {
            Set<String> keys = redisTemplate.keys("reconnection:*");

            if (keys == null || keys.isEmpty()) {
                return;
            }

            log.debug("Found {} pending reconnections to monitor", keys.size());

            for (String key : keys) {
                String[] parts = key.split(":");
                if (parts.length != 3) {
                    log.warn("Invalid reconnection key format: {}", key);
                    continue;
                }

                UUID matchId = UUID.fromString(parts[1]);
                UUID userId = UUID.fromString(parts[2]);

                Long ttl = redisTemplate.getExpire(key);

                if (ttl != null && ttl > 0 && ttl < 60) {
                    log.info("Reconnection expiring soon: matchId={}, userId={}, ttl={}s",
                            matchId, userId, ttl);
                }
            }

        } catch (Exception e) {
            log.error("Error checking expired reconnections", e);
        }
    }

    public int cleanupStaleReconnections() {
        Set<String> keys = redisTemplate.keys("reconnection:*");

        if (keys == null || keys.isEmpty()) {
            return 0;
        }

        int cleaned = 0;
        for (String key : keys) {
            Long ttl = redisTemplate.getExpire(key);

            if (ttl != null && ttl <= 0) {
                redisTemplate.delete(key);
                cleaned++;
            }
        }

        log.info("Manual cleanup completed: {} stale reconnections removed", cleaned);
        return cleaned;
    }
}