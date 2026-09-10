package com.backend.lifeplatform.outbox.enums;

public enum PublishResult {
    PUBLISHED_NOW,
    ALREADY_PUBLISHED,
    NOT_FOUND,
    INVALID_STATE
}