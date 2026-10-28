package com.myprueba.shared;

import org.springframework.http.HttpStatus;

/** An error that maps to an RFC 9457 problem response with a stable {@code code}. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final java.util.Map<String, Object> properties;

    public ApiException(HttpStatus status, String code) {
        this(status, code, java.util.Map.of());
    }

    /** {@code properties} are extra, non-sensitive problem fields the client can show (e.g. a product name). */
    public ApiException(HttpStatus status, String code, java.util.Map<String, Object> properties) {
        super(code);
        this.status = status;
        this.code = code;
        this.properties = java.util.Map.copyOf(properties);
    }

    public java.util.Map<String, Object> properties() {
        return properties;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
