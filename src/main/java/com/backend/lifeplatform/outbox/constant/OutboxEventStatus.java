package com.backend.lifeplatform.outbox.constant;

/**
 * 秒杀 Outbox 事件状态常量。
 * <p>状态机：NEW → PROCESSING → SUCCESS；失败重试走 RETRY_WAIT，超过最大重试次数置为 FAILED。</p>
 */
public final class OutboxEventStatus {

    /** 新建，待投递 */
    public static final String NEW = "NEW";
    /** 投递中（已被某个消费者认领，尚未确认结果） */
    public static final String PROCESSING = "PROCESSING";
    /** 投递成功 */
    public static final String SUCCESS = "SUCCESS";
    /** 投递失败，等待下次重试 */
    public static final String RETRY_WAIT = "RETRY_WAIT";
    /** 超过最大重试次数，进入人工或死信处理 */
    public static final String FAILED = "FAILED";

    private OutboxEventStatus() {
    }
}
