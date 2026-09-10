package com.backend.lifeplatform.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应 user 表。
 * password 保存 BCrypt 密文，不保存明文密码。
 */
@Data
@TableName("user")
public class User {

    /** 用户 ID，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 登录手机号 */
    private String phone;

    /** BCrypt 密文 */
    private String password;

    /** 用户角色：USER 普通用户，ADMIN 管理员 */
    private String role;

    /** 用户状态：1 正常，0 禁用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
