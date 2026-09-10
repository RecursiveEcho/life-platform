-- 在当前 user 表上补齐注册和角色分权所需的数据库结构。
-- 执行一次即可。

ALTER TABLE `user`
    ADD COLUMN `role` varchar(20) NOT NULL DEFAULT 'USER'
        COMMENT '用户角色：USER 普通用户，ADMIN 管理员'
        AFTER `password`;

ALTER TABLE `user`
    ADD UNIQUE KEY `uk_user_phone` (`phone`);

-- 需要管理员账号时，再手动执行：
-- UPDATE `user` SET `role` = 'ADMIN' WHERE `phone` = '你的手机号';
