package com.rpo.mimico.exceptions;

public class RoleNotFoundException extends RuntimeException {

    public RoleNotFoundException() {
        super("Default role not configured");
    }

    public RoleNotFoundException(String message) {
        super(message);
    }

    public RoleNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
