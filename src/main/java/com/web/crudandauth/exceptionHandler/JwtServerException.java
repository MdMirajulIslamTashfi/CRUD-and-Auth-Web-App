package com.web.crudandauth.exceptionHandler;

public class JwtServerException extends RuntimeException {
    public JwtServerException(String message) {
        super("JWT service error: " + message);
    }
}