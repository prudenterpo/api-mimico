package com.rpo.mimico.exceptions;

public class InvalidTokenException extends AuthenticationException {

    public InvalidTokenException() {
        super("Invalid authentication token");
    }

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}