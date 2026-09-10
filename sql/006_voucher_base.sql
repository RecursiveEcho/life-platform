USE `life_platform`;

-- 旧数据库若已经手动创建过这些表，CREATE IF NOT EXISTS 不会破坏现有数据。
CREATE TABLE IF NOT EXISTS `vouchers`
(
    `id`              bigint        NOT NULL AUTO_INCREMENT,
    `shop_id`         bigint        NOT NULL COMMENT '店铺 ID',
    `title`           varchar(100)  NOT NULL COMMENT '优惠券标题',
    `sub_title`       varchar(255)           DEFAULT NULL COMMENT '副标题/描述',
    `discount_amount` decimal(10, 2) NOT NULL COMMENT '优惠金额',
    `pay_value`       decimal(10, 2) NOT NULL COMMENT '实际支付金额',
    `stock`           int           NOT NULL DEFAULT 0 COMMENT '普通券库存',
    `begin_time`      datetime               DEFAULT NULL COMMENT '可售开始时间',
    `end_time`        datetime               DEFAULT NULL COMMENT '可售结束时间',
    `create_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`     datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_voucher_shop` (`shop_id`),
    KEY `idx_voucher_time` (`begin_time`, `end_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '优惠券主表';

CREATE TABLE IF NOT EXISTS `seckill_vouchers`
(
    `voucher_id` bigint   NOT NULL COMMENT '优惠券 ID',
    `stock`      int      NOT NULL DEFAULT 0 COMMENT '秒杀库存',
    `begin_time` datetime NOT NULL COMMENT '秒杀开始时间',
    `end_time`   datetime NOT NULL COMMENT '秒杀结束时间',
    PRIMARY KEY (`voucher_id`),
    KEY `idx_seckill_time` (`begin_time`, `end_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '秒杀优惠券基础表';

CREATE TABLE IF NOT EXISTS `voucher_orders`
(
    `id`           bigint      NOT NULL COMMENT '订单 ID，由应用侧生成',
    `user_id`      bigint      NOT NULL COMMENT '用户 ID',
    `voucher_id`   bigint      NOT NULL COMMENT '优惠券 ID',
    `order_status` tinyint     NOT NULL DEFAULT 1 COMMENT '1已领取，2已支付，3已取消，4已完成',
    `create_time`  datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `pay_time`     datetime             DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_voucher` (`user_id`, `voucher_id`),
    KEY `idx_order_user_time` (`user_id`, `create_time`),
    KEY `idx_order_voucher` (`voucher_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '优惠券订单';
