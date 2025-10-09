package com.rpo.mimico.exceptions;

public class EmailAlreadyExistsException extends AuthenticationException {

    public EmailAlreadyExistsException() {
        super("E-mail already exists");
    }

    public EmailAlreadyExistsException(String message) {
        super(message);
    }

    public EmailAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
