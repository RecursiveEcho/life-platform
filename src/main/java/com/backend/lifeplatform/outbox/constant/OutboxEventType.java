package com.backend.lifeplatform.outbox.constant;

/**
 * 秒杀 Outbox 事件类型常量。
 * <p>目前只有「秒杀活动发布」一种事件；后续若新增其他需要可靠投递的业务事件，在此追加类型。</p>
 */
public final class OutboxEventType {

    /** 秒杀活动发布事件 */
    public static final String SECKILL_ACTIVITY_PUBLISH = "SECKILL_ACTIVITY_PUBLISH";

    private OutboxEventType() {
    }
}
