package com.backend.lifeplatform.user.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 登录成功后返回的用户信息和访问令牌。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {
    /** 用户 ID */
    private Long userId;
    /** 手机号 */
    private String phone;
    /** JWT 访问令牌 */
    private String token;
}
