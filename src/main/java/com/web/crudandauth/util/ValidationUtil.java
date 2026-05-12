package com.web.crudandauth.util;


import com.web.crudandauth.exceptionHandler.ValidationException;

public class ValidationUtil {

    private static final String NAME_PATTERN = "^[a-zA-Z ]+$";
    private static final String EMAIL_PATTERN ="^[0-9a-z._+-]+@[a-z0-9]+\\.[a-z0-9]{2,20}$";
    private static final String INPUT_PATTERN ="^[a-zA-Z0-9._@ +-]+$";


    private ValidationUtil() {
        // prevent instantiation
    }

    public static void validateName(String name) {
        if (name == null || !name.matches(NAME_PATTERN)) {
            throw new ValidationException("Name format is invalid");
        }
    }

    public static void validateEmail(String email) {
        if (email == null || !email.matches(EMAIL_PATTERN)) {
            throw new ValidationException("Email format is invalid");
        }
    }

    public static void validateInput(String input) {
        if (input == null || !input.matches(INPUT_PATTERN)) {
            throw new ValidationException("Input format is invalid");
        }
    }
}

