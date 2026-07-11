package com.talentos.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {
    private final HttpStatus status;

    public BusinessException(String message) {
        super(message);
        this.status = HttpStatus.UNPROCESSABLE_ENTITY;
    }

    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}
