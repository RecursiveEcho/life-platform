package com.backend.lifeplatform.common.trace;

/** 请求链路追踪使用的常量。 */
public final class TraceConstants {

    private TraceConstants() {
    }

    /** 请求头和响应头中的链路 ID。 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** MDC 中保存链路 ID 的 key。 */
    public static final String MDC_TRACE_ID = "traceId";
}
