package com.talentos.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Map;

@Getter
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ValidationException extends RuntimeException {
    private final Map<String, String> errors;

    public ValidationException(String message) {
        super(message);
        this.errors = null;
    }

    public ValidationException(String message, Map<String, String> errors) {
        super(message);
        this.errors = errors;
    }
}
