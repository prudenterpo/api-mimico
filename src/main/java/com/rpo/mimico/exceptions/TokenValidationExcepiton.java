package com.rpo.mimico.exceptions;

public class TokenValidationExcepiton extends RuntimeException {

    public TokenValidationExcepiton(String message) {
        super(message);
    }

    public TokenValidationExcepiton(String message, Throwable cause) {
        super(message, cause);
    }
}

