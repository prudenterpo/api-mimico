package com.rpo.mimico.services;

import com.rpo.mimico.dtos.RegisterRequestDTO;
import com.rpo.mimico.entities.AuthCredentialsEntity;
import com.rpo.mimico.entities.RolesEntity;
import com.rpo.mimico.entities.UserEntity;
import com.rpo.mimico.exceptions.EmailAlreadyExistsException;
import com.rpo.mimico.exceptions.NicknameAlreadyExistsException;
import com.rpo.mimico.repositories.AuthCredentialsRepository;
import com.rpo.mimico.repositories.RoleRepository;
import com.rpo.mimico.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthCredentialsRepository authCredentialsRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void registerRejectsDuplicateEmail() {
        RegisterRequestDTO request = new RegisterRequestDTO("player@example.test", "Strong1!", "player_1");
        when(authCredentialsRepository.findByEmail("player@example.test"))
                .thenReturn(Optional.of(new AuthCredentialsEntity()));

        assertThrows(EmailAlreadyExistsException.class, () -> userService.register(request));
    }

    @Test
    void registerRejectsDuplicateNickname() {
        RegisterRequestDTO request = new RegisterRequestDTO("player@example.test", "Strong1!", "player_1");
        when(authCredentialsRepository.findByEmail("player@example.test")).thenReturn(Optional.empty());
        when(userRepository.existsByNickname("player_1")).thenReturn(true);

        assertThrows(NicknameAlreadyExistsException.class, () -> userService.register(request));
    }

    @Test
    void registerRequiresDefaultPlayerRole() {
        RegisterRequestDTO request = new RegisterRequestDTO("player@example.test", "Strong1!", "player_1");
        when(authCredentialsRepository.findByEmail("player@example.test")).thenReturn(Optional.empty());
        when(userRepository.existsByNickname("player_1")).thenReturn(false);
        when(roleRepository.findByName("PLAYER")).thenReturn(Optional.of(new RolesEntity("PLAYER", "Default player")));
        when(passwordEncoder.encode("Strong1!")).thenReturn("hash");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setId(java.util.UUID.randomUUID());
            return user;
        });

        userService.register(request);
    }
}
