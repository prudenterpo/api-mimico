package com.rpo.mimico.exceptions;

public class NicknameAlreadyExistsException extends AuthenticationException {

    public NicknameAlreadyExistsException() {
        super("Nickname already exists");
    }

    public NicknameAlreadyExistsException(String message) {
        super(message);
    }

    public NicknameAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
