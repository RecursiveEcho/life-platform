package com.backend.lifeplatform.common.utils;

import com.backend.lifeplatform.common.enums.ErrorCode;
import com.backend.lifeplatform.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从当前请求的 SecurityContext 中读取 JWT 过滤器恢复的用户身份。
 */
public final class SecurityUserContext {

    private SecurityUserContext() {
    }

    /**
     * 读取当前登录用户的 id。
     *
     * <p>JWT 过滤器把用户 id 存到了 {@code Authentication.details}（见 {@code JwtSecurityFilter}），
     * 这里统一取出并解析为 Long，供业务代码获取当前用户身份。</p>
     */
    public static Long requireUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getDetails() instanceof String details)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "当前登录身份无效");
        }
        try {
            return Long.valueOf(details);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "当前登录身份无效");
        }
    }

    /**
     * 判断当前用户是否拥有指定角色。
     *
     * <p>Spring Security 约定角色权限名带 {@code ROLE_} 前缀，故此处拼上前缀后再与
     * {@link org.springframework.security.core.GrantedAuthority#getAuthority()} 的返回值比较。</p>
     */
    public static boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + role)
                        .equals(authority.getAuthority()));
    }
}
