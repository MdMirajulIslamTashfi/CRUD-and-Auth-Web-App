package com.web.crudandauth.dtos.requests;

import lombok.Data;

@Data
public class PaginationRequestDto {
    private int page;
    private int size;
}
