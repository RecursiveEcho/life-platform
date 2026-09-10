-- 新环境初始化脚本：创建基础表和秒杀链路的核心约束。
-- 已有旧数据库请按 001 -> 002 -> 003 的顺序执行增量脚本，不要重复执行本文件中的 ALTER。

CREATE DATABASE IF NOT EXISTS `life_platform`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE `life_platform`;

CREATE TABLE IF NOT EXISTS `user`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT,
    `phone`       varchar(20)  NOT NULL,
    `password`    varchar(255) NOT NULL COMMENT 'BCrypt 密文',
    `role`        varchar(20)  NOT NULL DEFAULT 'USER' COMMENT 'USER 普通用户，ADMIN 管理员',
    `status`      tinyint      NOT NULL DEFAULT 1 COMMENT '1 正常，0 禁用',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_phone` (`phone`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `shop_reviews`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT,
    `user_id`     bigint       NOT NULL COMMENT '评价用户 ID',
    `shop_id`     bigint       NOT NULL COMMENT '店铺 ID',
    `order_id`    bigint       NOT NULL COMMENT '产生评价资格的订单 ID',
    `rating`      tinyint      NOT NULL COMMENT '评分：1-5',
    `content`     varchar(1000) NOT NULL COMMENT '评价内容',
    `status`      tinyint      NOT NULL DEFAULT 1 COMMENT '1正常，0隐藏',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_review_order_user` (`order_id`, `user_id`),
    KEY `idx_review_shop_status_time` (`shop_id`, `status`, `create_time`),
    KEY `idx_review_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '店铺评价';

CREATE TABLE IF NOT EXISTS `shops`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT,
    `name`        varchar(100) NOT NULL COMMENT '商铺名称',
    `type_id`     bigint       NOT NULL COMMENT '商铺类型ID',
    `address`     varchar(255)          DEFAULT NULL COMMENT '商铺地址',
    `longitude`   decimal(10, 6)        DEFAULT NULL COMMENT '经度',
    `latitude`    decimal(10, 6)        DEFAULT NULL COMMENT '纬度',
    `avg_price`   decimal(10, 2)        DEFAULT NULL COMMENT '人均价格',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_type_id` (`type_id`),
    KEY `idx_location` (`longitude`, `latitude`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

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

-- 下面的店铺权限字段让游客查询只看到 APPROVED + OPEN 的店铺，
-- owner_id 用于 Service 层校验商家只能修改自己的店铺。

ALTER TABLE shops
    ADD COLUMN owner_id        BIGINT DEFAULT NULL COMMENT '所属商家用户ID',
    ADD COLUMN audit_status    VARCHAR(20) NOT NULL DEFAULT 'APPROVED'
        COMMENT '审核状态：PENDING待审核，APPROVED已通过，REJECTED已驳回',
    ADD COLUMN business_status VARCHAR(20) NOT NULL DEFAULT 'OPEN'
        COMMENT '营业状态：OPEN营业中，CLOSED已歇业',
    ADD COLUMN deleted         TINYINT     NOT NULL DEFAULT 0
        COMMENT '逻辑删除：0未删除，1已删除';

ALTER TABLE shops
    add index idx_shop_owner_id (owner_id),
    add index idx_shop_audit_status (audit_status)
