package com.rpo.mimico.services;

import com.rpo.mimico.entities.UserEntity;
import com.rpo.mimico.dtos.UserProfileDTO;
import com.rpo.mimico.entities.RolesEntity;
import com.rpo.mimico.repositories.AuthCredentialsRepository;
import com.rpo.mimico.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineUsersService {

    private static final String ONLINE_USERS_KEY = "lobby:online";

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final AuthCredentialsRepository authCredentialsRepository;

    public void addUser(UUID userId) {
        redisTemplate.opsForSet().add(ONLINE_USERS_KEY, userId.toString());
        log.debug("User {} added to online users. Total online: {}", userId, getOnlineCount());
    }

    public void removeUser(UUID userId) {
        redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, userId.toString());
        log.debug("User {} removed from online users. Total online: {}", userId, getOnlineCount());
    }

    public Set<String> getOnlineUsers() {
        return redisTemplate.opsForSet().members(ONLINE_USERS_KEY);
    }

    public List<UserEntity> getOnlineUsersWithDetails() {
        Set<String> onlineUserIds = getOnlineUsers();
        if (onlineUserIds == null || onlineUserIds.isEmpty()) {
            return Collections.emptyList();
        }

        Set<UUID> uuids = onlineUserIds.stream()
                .map(UUID::fromString)
                .collect(Collectors.toSet());

        return userRepository.findAllById(uuids);
    }

    public List<UserProfileDTO> getOnlineUserProfiles() {
        return getOnlineUsersWithDetails().stream()
                .map(this::toUserProfile)
                .toList();
    }

    public UserProfileDTO getOnlineUserProfile(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));

        return toUserProfile(user);
    }

    public Long getOnlineCount() {
        return redisTemplate.opsForSet().size(ONLINE_USERS_KEY);
    }

    public boolean isUserOnline(UUID userId) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForSet().isMember(ONLINE_USERS_KEY, userId.toString())
        );
    }

    public void clearAll() {
        redisTemplate.delete(ONLINE_USERS_KEY);
        log.info("All online users cleared from Redis");
    }

    private UserProfileDTO toUserProfile(UserEntity user) {
        String email = authCredentialsRepository.findByUser_Id(user.getId())
                .map(credentials -> credentials.getEmail())
                .orElse("");

        Set<String> roles = user.getRoles().stream()
                .map(RolesEntity::getName)
                .collect(Collectors.toSet());

        return new UserProfileDTO(
                user.getId().toString(),
                email,
                user.getNickname(),
                user.getAvatarUrl(),
                roles,
                user.getCreatedAt()
        );
    }
}
