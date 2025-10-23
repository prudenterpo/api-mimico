package com.rpo.mimico.repositories;

import com.rpo.mimico.entities.MimeCardEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
public class MimeCardEntityRepositoryTest {

    @Autowired
    private MimeCardRepository mimeCardRepository;

    @Test
    public void shouldSaveMimeCard() {
        MimeCardEntity mimeCardEntity = new MimeCardEntity();
        mimeCardEntity.setDescription("Test description");
        mimeCardEntity.setCategory("Test Category");

        MimeCardEntity savedMimeCardEntity = mimeCardRepository.save(mimeCardEntity);

        assertNotNull(savedMimeCardEntity);
        assertEquals(savedMimeCardEntity, mimeCardEntity);
    }

    @Test
    public void shouldGetMimeCardById() {
        MimeCardEntity mimeCardEntity = new MimeCardEntity();
        mimeCardEntity.setDescription("Test Word");
        mimeCardEntity.setCategory("Test Category");
        mimeCardRepository.save(mimeCardEntity);

        MimeCardEntity foundMimeCardEntity = mimeCardRepository.findById(mimeCardEntity.getId()).orElse(null);

        assertNotNull(foundMimeCardEntity);
        assertEquals(mimeCardEntity, foundMimeCardEntity);
    }

    @Test
    public void shouldUpdateMimeCard() {
        MimeCardEntity mimeCardEntity = new MimeCardEntity();
        mimeCardEntity.setDescription("Test Word");
        mimeCardEntity.setCategory("Test Category");
        mimeCardRepository.save(mimeCardEntity);

        mimeCardEntity.setCategory("Updated Category");
        MimeCardEntity updatedMimeCardEntity = mimeCardRepository.save(mimeCardEntity);

        assertNotNull(updatedMimeCardEntity);
        assertEquals("Updated Category", updatedMimeCardEntity.getCategory());
    }

    @Test
    public void shouldDeleteMimeCard() {
        MimeCardEntity mimeCardEntity = new MimeCardEntity();
        mimeCardEntity.setDescription("Test Word");
        mimeCardEntity.setCategory("Test Category");
        mimeCardRepository.save(mimeCardEntity);

        mimeCardRepository.delete(mimeCardEntity);

        assertFalse(mimeCardRepository.existsById(mimeCardEntity.getId()));
    }
}
