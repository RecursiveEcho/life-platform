package com.backend.lifeplatform.outbox.exception;

public class OutboxPermanentException extends RuntimeException {
    public OutboxPermanentException(String message) {
        super(message);
    }

    public OutboxPermanentException(String message,Throwable cause) {
        super(message,cause);
    }
}
