package com.backend.lifeplatform.voucherOrders.service;

/**
 * 秒杀订单的数据库事务服务。
 *
 * <p>消息消费者通过该接口完成幂等检查、校验活动时间、条件扣减库存和创建订单；
 * 这些动作必须在同一个事务中执行，扣库存失败或建单失败时一起回滚。</p>
 */
public interface VoucherOrderTransactionService {

    /** 消费一条秒杀消息并尝试创建订单；重复消息返回 false。 */
    boolean createOrder(Long userId,Long voucherId,Long orderId);
}
