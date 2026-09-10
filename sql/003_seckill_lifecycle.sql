USE `life_platform`;

-- 为秒杀场次补充生命周期字段：创建后是 DRAFT，发布/下线由后续管理流程推进。
-- (voucher_id, begin_time, end_time) 索引服务于后台按状态和时间筛选活动。
ALTER TABLE `seckill_vouchers`
    ADD COLUMN `status` varchar(20) NOT NULL DEFAULT 'DRAFT'
        COMMENT '活动状态：DRAFT草稿，PUBLISHED已发布，OFFLINE已下线'
        AFTER `end_time`,
    ADD COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
        COMMENT '创建时间'
        AFTER `status`,
    ADD COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
        COMMENT '更新时间'
        AFTER `create_time`,
    ADD KEY `idx_seckill_status_time` (`status`, `begin_time`, `end_time`);
