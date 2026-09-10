package com.backend.lifeplatform.outbox.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SeckillPublishPayload {

    private Long voucherId;

    private Integer stock;

    private LocalDateTime beginTime;

    private LocalDateTime  endTime;
}
