package com.backend.lifeplatform.common.config;

import com.backend.lifeplatform.common.filter.JwtSecurityFilter;
import com.backend.lifeplatform.common.filter.TraceIdFilter;
import com.backend.lifeplatform.common.enums.ErrorCode;
import com.backend.lifeplatform.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** 无状态 JWT 安全配置；只把登录和公开查询接口放入白名单。 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    /** 允许跨域访问的源，逗号分隔。用 pattern 而非精确 origin，是为了支持任意端口（如 localhost:* 匹配 http://localhost:5173）。 */
    @Value("${app.cors.allowed-origin-patterns:http://localhost,http://localhost:*,http://127.0.0.1,http://127.0.0.1:*}")
    private String allowedOriginPatterns;

    private final JwtSecurityFilter jwtSecurityFilter;
    private final TraceIdFilter traceIdFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 规则自上而下按顺序匹配，命中即停止，所以宽泛的放行规则要写在前面。
                .authorizeHttpRequests(auth -> auth
                        // 浏览器 CORS 预检请求（OPTIONS）不携带认证信息，必须放行，否则跨域请求会被拒绝。
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Swagger/Knife4j 接口文档页相关静态资源放行，方便开发期浏览文档。
                        .requestMatchers(
                                "/doc.html",
                                "/swagger-ui/**",
                                "/swagger-resources/**",
                                "/webjars/**",
                                "/v3/api-docs/**")
                        .permitAll()
                        // 登录、注册无需登录即可访问。
                        .requestMatchers(HttpMethod.POST, "/api/users/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users/register").permitAll()
                        // 店铺只读查询对游客开放；写操作（创建/修改）需要对应角色。
                        .requestMatchers(HttpMethod.GET, "/api/shops", "/api/shops/**").permitAll()
                        // 优惠券列表和详情是用户浏览链路的一部分，对游客开放。
                        .requestMatchers(HttpMethod.GET, "/api/vouchers", "/api/vouchers/**").permitAll()
                        // 优惠券和秒杀场次的写操作只允许管理员或商家。
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/vouchers",
                                "/api/vouchers/create/seckillVoucher")
                        .hasAnyRole("ADMIN", "MERCHANT")
                        .requestMatchers(HttpMethod.PATCH, "/api/vouchers/**")
                        .hasAnyRole("ADMIN", "MERCHANT")
                        .requestMatchers(HttpMethod.PUT, "/api/vouchers/published/**")
                        .hasAnyRole("ADMIN", "MERCHANT")
                        // 店铺公开评价允许游客浏览，提交评价仍由默认规则要求登录。
                        .requestMatchers(HttpMethod.GET, "/api/reviews/shop/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/shops", "/api/shops/**").hasAnyRole("ADMIN","MERCHANT")
                        .requestMatchers(HttpMethod.PATCH, "/api/shops/**").hasAnyRole("ADMIN", "MERCHANT")
                        .requestMatchers("/api/merchant-applications/*/approve",
                                "/api/merchant-applications/*/reject").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,"/api/merchant-applications").hasRole("ADMIN")
                        // 其余接口默认要求登录。
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        // 未登录/令牌失效：返回 401（authenticationEntryPoint 触发）。
                        .authenticationEntryPoint((request, response, e) ->
                                writeSecurityError(response, HttpStatus.UNAUTHORIZED,
                                        ErrorCode.INVALID_TOKEN.getCode(), "请先登录或登录状态已失效"))
                        // 已登录但无权限：返回 403（accessDeniedHandler 触发，如非 ADMIN 调用写接口）。
                        .accessDeniedHandler((request, response, e) ->
                                writeSecurityError(response, HttpStatus.FORBIDDEN,
                                        ErrorCode.INVALID_OPERATION.getCode(), "没有权限执行该操作")))
                // 过滤链顺序：traceId 在最前（先放进 MDC），JWT 随后（解析出当前用户）。
                .addFilterBefore(jwtSecurityFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(traceIdFilter, JwtSecurityFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // allowCredentials 为 false 时，AllowedOrigins 不能用通配符 *，所以改用 AllowedOriginPatterns。
        configuration.setAllowedOriginPatterns(parseAllowedOriginPatterns());
        configuration.setAllowedMethods(
                Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // 无 Cookie/Session 鉴权（纯 JWT 放请求头），无需让浏览器带凭证。
        configuration.setAllowCredentials(false);
        // 显式暴露 X-Trace-Id，前端 JS 才能读到该响应头（否则被浏览器 CORS 隐藏）。
        configuration.setExposedHeaders(List.of("X-Trace-Id"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private List<String> parseAllowedOriginPatterns() {
        return Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .collect(Collectors.toList());
    }

    private void writeSecurityError(HttpServletResponse response,
                                    HttpStatus status,
                                    Integer code,
                                    String message) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code, message)));
    }
}
