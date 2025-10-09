package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.UsersEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UsersEntity, Long> {

    boolean existsByNickname(String nickname);

}
