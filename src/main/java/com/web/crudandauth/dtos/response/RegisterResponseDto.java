package com.web.crudandauth.dtos.response;

import com.web.crudandauth.enums.Roles;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponseDto {
    private boolean success;
    private String status;
    private String message;
    private String email;
    private Roles role;
}
