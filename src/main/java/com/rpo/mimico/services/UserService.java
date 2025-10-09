package com.rpo.mimico.services;

import com.rpo.mimico.dtos.RegisterRequestDTO;
import com.rpo.mimico.dtos.RegisterResponseDTO;
import com.rpo.mimico.entities.AuthCredentialsEntity;
import com.rpo.mimico.entities.RolesEntity;
import com.rpo.mimico.entities.UsersEntity;
import com.rpo.mimico.exceptions.EmailAlreadyExistsException;
import com.rpo.mimico.exceptions.NicknameAlreadyExistsException;
import com.rpo.mimico.exceptions.RoleNotFoundException;
import com.rpo.mimico.repositories.AuthCredentialsRepository;
import com.rpo.mimico.repositories.RoleRepository;
import com.rpo.mimico.repositories.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

        UsersEntity user = new UsersEntity();
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
}
