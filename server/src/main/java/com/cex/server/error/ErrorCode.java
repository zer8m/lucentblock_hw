package com.cex.server.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST),
    INVALID_PARAM(HttpStatus.BAD_REQUEST),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND),
    ORDER_NOT_CANCELABLE(HttpStatus.CONFLICT),
    ENGINE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
