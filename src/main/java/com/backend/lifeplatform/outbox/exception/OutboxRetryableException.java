package com.backend.lifeplatform.outbox.exception;

public class OutboxRetryableException extends RuntimeException {

    public OutboxRetryableException(String message) {
        super(message);
    }

    public OutboxRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
