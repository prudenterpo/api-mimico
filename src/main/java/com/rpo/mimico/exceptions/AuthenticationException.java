package com.rpo.mimico.exceptions;

public abstract class AuthenticationException extends RuntimeException {

    protected AuthenticationException(String message) {
        super(message);
    }

    protected AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
