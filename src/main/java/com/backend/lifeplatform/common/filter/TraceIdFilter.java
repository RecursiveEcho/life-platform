package com.backend.lifeplatform.common.filter;

import com.backend.lifeplatform.common.trace.TraceConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** 为每次请求生成并透传链路 ID，便于定位一条请求的日志。 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        // 优先沿用上游传入的 traceId（网关/前端已生成时），否则本服务自己生成一个。
        String traceId = request.getHeader(TraceConstants.TRACE_ID_HEADER);
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        // 放进 MDC，让本次请求打的所有日志自动带上 traceId（需 logback 配置 %X{traceId} 才会输出）。
        MDC.put(TraceConstants.MDC_TRACE_ID, traceId);
        // 写回响应头，方便前端在报错时把 traceId 反馈给后端定位问题。
        response.setHeader(TraceConstants.TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 关键：请求结束必须清理 MDC，否则线程复用会串到下一个无关请求的日志里。
            MDC.remove(TraceConstants.MDC_TRACE_ID);
        }
    }
}
