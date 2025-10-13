package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.UsersEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRepository extends JpaRepository<UsersEntity, UUID> {

    boolean existsByNickname(String nickname);

}
