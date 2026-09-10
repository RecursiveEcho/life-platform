package com.backend.lifeplatform.voucherOrders.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RabbitMQ 中传递的秒杀订单消息。
 *
 * <p>订单 id 在 HTTP 请求阶段预先生成，消费者直接使用它落库，
 * 因此重试同一条消息不会因为重新生成 id 而产生另一笔订单。</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class VoucherOrderMessage {

    /** 发起抢券请求的用户 id。 */
    private Long userId;

    /** 被抢购的秒杀券 id。 */
    private Long voucherId;

    /** 预先生成的订单 id，也是消息幂等追踪标识。 */
    private Long orderId;
}
