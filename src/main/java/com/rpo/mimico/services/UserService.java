package com.rpo.mimico.services;

import com.rpo.mimico.dtos.RegisterRequestDTO;
import com.rpo.mimico.dtos.RegisterResponseDTO;
import com.rpo.mimico.dtos.UserProfileDTO;
import com.rpo.mimico.entities.AuthCredentialsEntity;
import com.rpo.mimico.entities.RolesEntity;
import com.rpo.mimico.entities.UserEntity;
import com.rpo.mimico.exceptions.EmailAlreadyExistsException;
import com.rpo.mimico.exceptions.NicknameAlreadyExistsException;
import com.rpo.mimico.exceptions.RoleNotFoundException;
import com.rpo.mimico.exceptions.UserNotFoundException;
import com.rpo.mimico.repositories.AuthCredentialsRepository;
import com.rpo.mimico.repositories.RoleRepository;
import com.rpo.mimico.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final String PLAYER_DEFAULT_ROLE_NAME = "PLAYER";
    private final UserRepository userRepository;
    private final AuthCredentialsRepository authCredentialsRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public RegisterResponseDTO register(RegisterRequestDTO reguest) {
        log.info("Starting user registration for e-mail: {}", reguest.email());

        if (authCredentialsRepository.findByEmail(reguest.email()).isPresent()) {
            log.warn("Registration attempt with existing email: {}", reguest.email());
            throw new EmailAlreadyExistsException();
        }

        if (userRepository.existsByNickname(reguest.nickname())) {
            log.warn("Registration attempt with existing nickname: {}", reguest.nickname());
            throw new NicknameAlreadyExistsException();
        }

        RolesEntity playerRole = roleRepository.findByName(PLAYER_DEFAULT_ROLE_NAME)
                .orElseThrow(() -> {
                    log.error("Default role '{}' not found in database", PLAYER_DEFAULT_ROLE_NAME);
                    return new RoleNotFoundException();
                });

        UserEntity user = new UserEntity();
        user.setNickname(reguest.nickname());
        user.getRoles().add(playerRole);
        user = userRepository.save(user);

        log.debug("User entity created with ID: {} and role: {}", user.getId(), PLAYER_DEFAULT_ROLE_NAME);

        AuthCredentialsEntity credentials = new AuthCredentialsEntity();
        credentials.setEmail(reguest.email());
        credentials.setPasswordHash(passwordEncoder.encode(reguest.password()));
        credentials.setUser(user);
        authCredentialsRepository.save(credentials);

        log.info("User registered successfully: {}", reguest.email());

        return new RegisterResponseDTO(
                user.getId().toString(),
                reguest.email(),
                user.getNickname(),
                "User registered successfully"
        );
    }

    public UserProfileDTO getUserProfile(UUID userId) {
        log.info("Fetching user profile for ID: {}", userId);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("User not found with ID: {}", userId);
                    return new UserNotFoundException();
                });

        AuthCredentialsEntity credentials = authCredentialsRepository.findByUser_Id(userId)
                .orElseThrow(() -> {
                    log.error("Auth credentials not found for user ID: {}", userId);
                    return new UserNotFoundException();
                });

        Set<String> roleNames = user.getRoles().stream()
                .map(RolesEntity::getName)
                .collect(Collectors.toSet());

        log.debug("User profile retrieved successfully for ID: {}", userId);

        return new UserProfileDTO(
                user.getId().toString(),
                credentials.getEmail(),
                user.getNickname(),
                user.getAvatarUrl(),
                roleNames,
                user.getCreatedAt()
        );
    }
}
