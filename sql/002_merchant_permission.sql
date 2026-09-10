USE `life_platform`;

ALTER TABLE `user`
    MODIFY COLUMN `role` varchar(20) NOT NULL DEFAULT 'USER'
        COMMENT '用户角色：USER 普通用户，MERCHANT 商家，ADMIN 平台管理员';

ALTER TABLE `shops`
    ADD COLUMN `owner_id` bigint DEFAULT NULL COMMENT '店铺所属商家用户 ID' AFTER `type_id`,
    ADD COLUMN `audit_status` varchar(20) NOT NULL DEFAULT 'APPROVED'
        COMMENT '审核状态：PENDING 待审核，APPROVED 已通过，REJECTED 已驳回' AFTER `owner_id`,
    ADD COLUMN `business_status` varchar(20) NOT NULL DEFAULT 'OPEN'
        COMMENT '营业状态：OPEN 营业中，CLOSED 已歇业' AFTER `audit_status`,
    ADD COLUMN `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除' AFTER `business_status`,
    ADD KEY `idx_shop_owner_id` (`owner_id`),
    ADD KEY `idx_shop_audit_status` (`audit_status`);

CREATE TABLE IF NOT EXISTS `merchant_application` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL COMMENT '申请人用户 ID',
    `business_name` varchar(100) NOT NULL COMMENT '商家名称',
    `contact_name` varchar(50) NOT NULL COMMENT '联系人姓名',
    `contact_phone` varchar(20) NOT NULL COMMENT '联系人手机号',
    `status` varchar(20) NOT NULL DEFAULT 'PENDING'
        COMMENT '审核状态：PENDING 待审核，APPROVED 已通过，REJECTED 已驳回',
    `reject_reason` varchar(255) DEFAULT NULL COMMENT '驳回原因',
    `reviewed_by` bigint DEFAULT NULL COMMENT '审核管理员用户 ID',
    `reviewed_at` datetime DEFAULT NULL COMMENT '审核时间',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_merchant_application_user` (`user_id`),
    KEY `idx_merchant_application_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `user`
    ADD COLUMN `avatar` varchar(255) DEFAULT NULL COMMENT '用户头像 URL' AFTER `phone`;
