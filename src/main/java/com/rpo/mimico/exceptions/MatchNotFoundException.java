package com.rpo.mimico.exceptions;

import java.util.UUID;

public class MatchNotFoundException extends RuntimeException {

    public MatchNotFoundException(UUID id) {
        super("Match not found: " + id);
    }
}
