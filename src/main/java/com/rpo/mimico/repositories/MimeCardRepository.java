package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.MimeCard;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MimeCardRepository extends JpaRepository<MimeCard, Long> {
}
