USE `life_platform`;

CREATE TABLE IF NOT EXISTS `seckill_outbox_event`
(
    `id`              bigint       NOT NULL AUTO_INCREMENT,
    `event_id`        varchar(64)  NOT NULL COMMENT '事件唯一标识',
    `event_type`      varchar(64)  NOT NULL COMMENT '事件类型',
    `aggregate_id`    bigint       NOT NULL COMMENT '业务对象ID，此处为voucher_id',
    `payload`         json         NOT NULL COMMENT '事件业务快照',
    `status`          varchar(20)  NOT NULL DEFAULT 'NEW' COMMENT 'NEW/PROCESSING/SUCCESS/RETRY_WAIT/FAILED',
    `retry_count`     int          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `next_retry_time` datetime              DEFAULT NULL COMMENT '下一次允许处理时间',
    `last_error`      varchar(1000)         DEFAULT NULL COMMENT '最后一次失败原因',
    `claim_token`     varchar(64)           DEFAULT NULL COMMENT '当前租约持有者令牌',
    `lease_until`     datetime              DEFAULT NULL COMMENT '当前处理租约到期时间',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_outbox_event_id` (`event_id`),
    KEY `idx_outbox_pending` (`status`, `next_retry_time`, `id`),
    KEY `idx_outbox_lease` (`status`, `lease_until`, `id`),
    KEY `idx_outbox_aggregate` (`event_type`, `aggregate_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '可靠事件发件箱';
