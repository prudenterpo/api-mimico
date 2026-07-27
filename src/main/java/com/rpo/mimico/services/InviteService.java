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
    private static final String INVITE_LOOKUP_KEY_PREFIX = "invite:lookup:";
    private static final long INVITE_TIMEOUT_SECONDS = 60;

    private final StringRedisTemplate redisTemplate;

    public UUID createInvite(UUID tableId, UUID invitedUserId, UUID hostUserId) {
        UUID inviteId = UUID.randomUUID();
        String inviteKey = buildInviteKey(inviteId);
        String lookupKey = buildLookupKey(tableId, invitedUserId);

        redisTemplate.opsForValue().set(inviteKey + ":table", tableId.toString(), INVITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(inviteKey + ":user", invitedUserId.toString(), INVITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(inviteKey + ":host", hostUserId.toString(), INVITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(lookupKey, inviteId.toString(), INVITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        log.debug("Invite created: tableId={}, invitedUserId={}, expiresIn={}s",
                tableId, invitedUserId, INVITE_TIMEOUT_SECONDS);
        return inviteId;
    }

    public boolean inviteExists(UUID tableId, UUID invitedUserId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(buildLookupKey(tableId, invitedUserId)));
    }

    public boolean inviteExists(UUID tableId, UUID inviteId, UUID invitedUserId) {
        String storedTable = redisTemplate.opsForValue().get(buildInviteKey(inviteId) + ":table");
        String storedUser = redisTemplate.opsForValue().get(buildInviteKey(inviteId) + ":user");
        return tableId.toString().equals(storedTable) && invitedUserId.toString().equals(storedUser);
    }

    public void removeInvite(UUID tableId, UUID invitedUserId) {
        String lookupKey = buildLookupKey(tableId, invitedUserId);
        String inviteId = redisTemplate.opsForValue().get(lookupKey);
        if (inviteId != null) {
            removeInvite(UUID.fromString(inviteId));
        }
        redisTemplate.delete(lookupKey);
        log.debug("Invite removed: tableId={}, invitedUserId={}", tableId, invitedUserId);
    }

    public void removeInvite(UUID inviteId) {
        String inviteKey = buildInviteKey(inviteId);
        redisTemplate.delete(inviteKey + ":table");
        redisTemplate.delete(inviteKey + ":user");
        redisTemplate.delete(inviteKey + ":host");
    }

    public long inviteTimeoutSeconds() {
        return INVITE_TIMEOUT_SECONDS;
    }

    private String buildInviteKey(UUID inviteId) {
        return INVITE_KEY_PREFIX + inviteId;
    }

    private String buildLookupKey(UUID tableId, UUID invitedUserId) {
        return INVITE_LOOKUP_KEY_PREFIX + tableId + ":" + invitedUserId;
    }
}
