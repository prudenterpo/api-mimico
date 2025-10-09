package com.rpo.mimico.exceptions;

public class TokenValidationException extends AuthenticationException {

    public TokenValidationException() {
        super("Error validating authentication token");
    }

    public TokenValidationException(String message) {
        super(message);
    }

    public TokenValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}