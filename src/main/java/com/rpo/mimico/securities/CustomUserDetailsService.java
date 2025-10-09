package com.rpo.mimico.securities;

import com.rpo.mimico.entities.AuthCredentialsEntity;
import com.rpo.mimico.repositories.AuthCredentialsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private static final String ROLE = "ROLE_";
    private final AuthCredentialsRepository authCredentialsRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("Loading user by e-mail: {}", email);

        AuthCredentialsEntity authCredentialsEntity = authCredentialsRepository
                .findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("User not found with email: {}", email);
                    return new UsernameNotFoundException("User not found with e-mail: " + email);
                });
        log.debug("User found: {}", email);

        List<SimpleGrantedAuthority> authorities = authCredentialsEntity.getUser().getRoles()
                .stream()
                .map(role -> new SimpleGrantedAuthority(ROLE + role.getName()))
                .toList();

        log.debug("User {} has roles: {}", email, authorities);

        return User.builder()
                .username(authCredentialsEntity.getEmail())
                .password(authCredentialsEntity.getPasswordHash())
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }
}
