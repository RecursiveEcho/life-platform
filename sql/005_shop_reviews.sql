USE `life_platform`;

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
