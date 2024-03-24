package com.rpo.mimico.entities;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MimeCardTest {

    @Test
    public void testMimeCardCreation() {
        MimeCard mimeCard = new MimeCard();
        mimeCard.setCategory("Object");
        mimeCard.setDescription("A thing");

        assertEquals("Object", mimeCard.getCategory());
        assertEquals("A thing", mimeCard.getDescription());
    }
}
