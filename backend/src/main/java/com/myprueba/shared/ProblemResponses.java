package com.myprueba.shared;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/** Builds RFC 9457 problem bodies with a stable {@code code} the frontend translates. */
public final class ProblemResponses {

    private ProblemResponses() {}

    public static ProblemDetail problem(HttpStatus status, String code, List<FieldViolation> errors) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        if (!errors.isEmpty()) {
            problem.setProperty(
                    "errors",
                    errors.stream()
                            .map(e -> Map.of("field", e.field(), "code", e.code()))
                            .toList());
        }
        return problem;
    }

    public static ResponseEntity<ProblemDetail> response(HttpStatus status, String code, List<FieldViolation> errors) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem(status, code, errors));
    }
}
