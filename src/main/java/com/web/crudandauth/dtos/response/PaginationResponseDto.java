package com.web.crudandauth.dtos.response;

import com.web.crudandauth.entities.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class PaginationResponseDto {
    private List<User> data;
    private int page;
    private int size;
    private int totalElements;
    private int totalPages;
}
