package com.web.crudandauth.exceptionHandler;

public class InvalidPasswordException extends RuntimeException {
    public InvalidPasswordException(String s) {
        super("The password you entered is incorrect");
    }
}