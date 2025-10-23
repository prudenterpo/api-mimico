package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.MimeCardEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MimeCardRepository extends JpaRepository<MimeCardEntity, Long> {
}
