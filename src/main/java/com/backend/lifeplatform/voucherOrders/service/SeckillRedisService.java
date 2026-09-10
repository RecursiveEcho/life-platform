package com.backend.lifeplatform.voucherOrders.service;

import java.time.LocalDateTime;

/**
 * 秒杀请求在 Redis 中的资格预扣服务。
 *
 * <p>Redis 只负责高并发入口的快速拦截，真正落库仍由消息消费者中的 MySQL 事务完成。
 * 如果消息投递失败或进入死信队列，调用方必须执行补偿，恢复库存和用户抢购标记。</p>
 */
public interface SeckillRedisService {

    /**
     * 原子校验库存和一人一单，并预扣 Redis 库存。
     */
    void preDeduct(Long voucherId, Long userId);

    /**
     * 撤销预扣结果；补偿脚本只会撤销对应用户的一次成功预扣。
     */
    void compensatePreDeduct(Long voucherId, Long userId);

    /** 把秒杀活动的库存与起止时间写入 Redis（幂等，活动已存在时不覆盖）。 */
    boolean ensureActivity(
            Long voucherId,
            Integer stock,
            LocalDateTime beginTime,
            LocalDateTime endTime
    );

    /** 把 Redis 中已初始化的秒杀活动激活为可抢购状态。 */
    void activateActivity(Long voucherId);
}
