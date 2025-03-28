package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.MimeCard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
public class MimeCardRepositoryTest {

    @Autowired
    private MimeCardRepository mimeCardRepository;

    @Test
    public void shouldSaveMimeCard() {
        MimeCard mimeCard = new MimeCard();
        mimeCard.setDescription("Test description");
        mimeCard.setCategory("Test Category");

        MimeCard savedMimeCard = mimeCardRepository.save(mimeCard);

        assertNotNull(savedMimeCard);
        assertEquals(savedMimeCard, mimeCard);
    }

    @Test
    public void shouldGetMimeCardById() {
        MimeCard mimeCard = new MimeCard();
        mimeCard.setDescription("Test Word");
        mimeCard.setCategory("Test Category");
        mimeCardRepository.save(mimeCard);

        MimeCard foundMimeCard = mimeCardRepository.findById(mimeCard.getId()).orElse(null);

        assertNotNull(foundMimeCard);
        assertEquals(mimeCard, foundMimeCard);
    }

    @Test
    public void shouldUpdateMimeCard() {
        MimeCard mimeCard = new MimeCard();
        mimeCard.setDescription("Test Word");
        mimeCard.setCategory("Test Category");
        mimeCardRepository.save(mimeCard);

        mimeCard.setCategory("Updated Category");
        MimeCard updatedMimeCard = mimeCardRepository.save(mimeCard);

        assertNotNull(updatedMimeCard);
        assertEquals("Updated Category", updatedMimeCard.getCategory());
    }

    @Test
    public void shouldDeleteMimeCard() {
        MimeCard mimeCard = new MimeCard();
        mimeCard.setDescription("Test Word");
        mimeCard.setCategory("Test Category");
        mimeCardRepository.save(mimeCard);

        mimeCardRepository.delete(mimeCard);

        assertFalse(mimeCardRepository.existsById(mimeCard.getId()));
    }
}
