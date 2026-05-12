package com.web.crudandauth.dtos.requests;

import com.web.crudandauth.enums.Roles;
import lombok.Data;

@Data
public class RegisterRequestDto {
    private String id;
    private String honor;
    private String firstName;
    private String lastName;
    private String gender;
    private String email;
    private String password;
    private String confirmPassword;
    private Roles role;
}
