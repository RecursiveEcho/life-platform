package com.backend.lifeplatform.common.filter;

import com.backend.lifeplatform.common.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** 从 Bearer JWT 中恢复当前用户，交给 Spring Security 做后续鉴权。 */
@Component
@RequiredArgsConstructor
public class JwtSecurityFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (StringUtils.hasText(token)) {
            try {
                Claims claims = jwtUtils.parseClaims(token);
                // subject 是 "userId:phone"，按第一个冒号切成两段：前半是用户 id，后半是手机号。
                String subject = claims.getSubject();
                String[] parts = subject.split(":", 2);
                if (parts.length == 2 && StringUtils.hasText(parts[0])) {
                    String role = claims.get("role", String.class);
                    // Spring Security 用 "ROLE_" 前缀 + hasRole("ADMIN") 匹配，这里必须拼上前缀。
                    List<SimpleGrantedAuthority> authorities = StringUtils.hasText(role)
                            ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                            : List.of();
                    // principal 存手机号，details 存用户 id，方便后续业务代码拿到当前用户身份。
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(parts[1], null, authorities);
                    authentication.setDetails(parts[0]);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception ignored) {
                // 无效令牌保持未认证状态，由 SecurityFilterChain 返回 401。
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            // "Bearer " 共 7 个字符，substring(7) 取到纯 token 部分。
            return authorization.substring(7).trim();
        }
        // 兼容旧项目使用的 token 请求头，便于迁移已有前端代码。
        return request.getHeader("token");
    }
}
