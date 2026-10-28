package com.myprueba.shared;

import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Runs before Spring's default problem handler so every error carries our {@code code}. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ApiExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    ResponseEntity<ProblemDetail> validation(ValidationException e) {
        return ProblemResponses.response(e.status(), e.code(), e.violations());
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> api(ApiException e) {
        ResponseEntity<ProblemDetail> response = ProblemResponses.response(e.status(), e.code(), List.of());
        e.properties().forEach(response.getBody()::setProperty);
        return response;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadable() {
        return ProblemResponses.response(HttpStatus.BAD_REQUEST, "malformed_request", List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> mediaType() {
        return ProblemResponses.response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type", List.of());
    }
}
