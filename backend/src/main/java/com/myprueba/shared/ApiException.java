package com.myprueba.shared;

import org.springframework.http.HttpStatus;

/** An error that maps to an RFC 9457 problem response with a stable {@code code}. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
