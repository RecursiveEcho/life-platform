package com.backend.lifeplatform.voucherOrders.mq;

import com.backend.lifeplatform.voucherOrders.service.SeckillRedisService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static com.backend.lifeplatform.common.config.SeckillRabbitMQ.DEAD_LETTER_QUEUE;

/**
 * 秒杀死信消费者。
 *
 * <p>进入死信意味着 Redis 已预扣但订单最终没有成功落库，
 * 因此这里只做一次可幂等的 Redis 库存和用户标记补偿。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoucherOrderDeadLetterConsumer {

    private final SeckillRedisService seckillRedisService;

    /** 补偿完成后确认死信；补偿失败则重新入队等待下一次处理。 */
    @RabbitListener(queues = DEAD_LETTER_QUEUE)
    public void consume(
            VoucherOrderMessage orderMessage,
            Message rabbitmessage,
            Channel channel
    ) throws IOException {
        long deliveryTag = rabbitmessage
                .getMessageProperties().getDeliveryTag();

        try {
            seckillRedisService.compensatePreDeduct(
                    orderMessage.getVoucherId(),
                    orderMessage.getUserId()
            );

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error(
                    "dead letter compensation failed, orderId={}",
                    orderMessage.getOrderId(),
                    e
            );

            channel.basicNack(deliveryTag, false, true);
        }
    }
}
