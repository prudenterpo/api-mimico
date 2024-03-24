package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
