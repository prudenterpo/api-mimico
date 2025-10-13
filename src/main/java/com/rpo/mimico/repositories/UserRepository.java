package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    boolean existsByNickname(String nickname);

}
