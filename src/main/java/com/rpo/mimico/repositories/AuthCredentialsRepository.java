package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.AuthCredentialsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthCredentialsRepository extends JpaRepository<AuthCredentialsEntity, UUID> {
    Optional<AuthCredentialsEntity> findByEmail(String email);
}