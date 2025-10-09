package com.rpo.mimico.exceptions;

public class TokenExpiredException extends AuthenticationException {

    public TokenExpiredException() {
        super("Authentication token has expired");
    }

    public TokenExpiredException(String message) {
        super(message);
    }

    public TokenExpiredException(String message, Throwable cause) {
        super(message, cause);
    }


}