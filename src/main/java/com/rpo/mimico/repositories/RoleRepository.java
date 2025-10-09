package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.RolesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<RolesEntity, UUID> {

    Optional<RolesEntity> findByName(String name);

    boolean existsByName(String name);
}
