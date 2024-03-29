package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.Team;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {
}
