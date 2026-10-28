package com.myprueba.shared;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

    public ConflictException(String code) {
        super(HttpStatus.CONFLICT, code);
    }
}
