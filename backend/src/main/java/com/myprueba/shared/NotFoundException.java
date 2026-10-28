package com.myprueba.shared;

import org.springframework.http.HttpStatus;

/** Missing resource. Also used for resources owned by another business, so both look identical. */
public class NotFoundException extends ApiException {

    public NotFoundException() {
        super(HttpStatus.NOT_FOUND, "not_found");
    }

    public NotFoundException(String code) {
        super(HttpStatus.NOT_FOUND, code);
    }
}
