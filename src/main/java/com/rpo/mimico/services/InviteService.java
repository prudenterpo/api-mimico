package com.rpo.mimico.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class InviteService {

    private static final String INVITE_KEY_PREFIX = "invite:";
    private static final long INVITE_TIMEOUT_SECONDS = 60;

    private final StringRedisTemplate redisTemplate;

    public void createInvite(UUID tableId, UUID invitedUserId, UUID hostUserId) {
        String key = buildInviteKey(tableId, invitedUserId);

        redisTemplate.opsForValue().set(key, hostUserId.toString(), INVITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        log.debug("Invite created: tableId={}, invitedUserId={}, expiresIn={}s",
                tableId, invitedUserId, INVITE_TIMEOUT_SECONDS);
    }

    public boolean inviteExists(UUID tableId, UUID invitedUserId) {
        String key = buildInviteKey(tableId, invitedUserId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void removeInvite(UUID tableId, UUID invitedUserId) {
        String key = buildInviteKey(tableId, invitedUserId);
        redisTemplate.delete(key);
        log.debug("Invite removed: tableId={}, invitedUserId={}", tableId, invitedUserId);
    }

    private String buildInviteKey(UUID tableId, UUID invitedUserId) {
        return INVITE_KEY_PREFIX + tableId + ":" + invitedUserId;
    }
}