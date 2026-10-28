package com.myprueba.shared;

import java.util.List;
import org.springframework.http.HttpStatus;

public class ValidationException extends ApiException {

    private final List<FieldViolation> violations;

    public ValidationException(List<FieldViolation> violations) {
        super(HttpStatus.BAD_REQUEST, "validation_failed");
        this.violations = List.copyOf(violations);
    }

    public static ValidationException of(String field, String code) {
        return new ValidationException(List.of(new FieldViolation(field, code)));
    }

    public List<FieldViolation> violations() {
        return violations;
    }
}
