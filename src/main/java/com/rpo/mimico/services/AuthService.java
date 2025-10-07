package com.rpo.mimico.services;

import com.rpo.mimico.securities.JwtProperties;
import com.rpo.mimico.dtos.LoginRequestDTO;
import com.rpo.mimico.dtos.LoginResponseDTO;
import com.rpo.mimico.entities.AuthCredentialsEntity;
import com.rpo.mimico.entities.UserEntity;
import com.rpo.mimico.exceptions.InvalidCredentialsException;
import com.rpo.mimico.repositories.AuthCredentialsRepository;
import com.rpo.mimico.securities.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String SESSION = "session:";
    private final AuthCredentialsRepository authCredentialsRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    public LoginResponseDTO login(LoginRequestDTO request) {
        AuthCredentialsEntity credentials = authCredentialsRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), credentials.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        UUID sessionId = UUID.randomUUID();
        UUID userId = credentials.getUser().getId();

        credentials.setLastSessionId(sessionId.toString());
        authCredentialsRepository.save(credentials);

        String redisKey = SESSION + userId;
        redisTemplate.opsForValue().set(redisKey, sessionId.toString(), jwtProperties.getExpiration());

        String token = jwtTokenProvider.generateToken(userId, credentials.getEmail(), sessionId.toString());

        UserEntity user = credentials.getUser();

        return new LoginResponseDTO(token, user.getId().toString(), user.getNickname());
    }
}
