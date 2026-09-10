package com.backend.lifeplatform.voucherOrders.mq;

import com.backend.lifeplatform.common.exception.BusinessException;
import com.backend.lifeplatform.voucherOrders.exception.DuplicateOrderMessageException;
import com.backend.lifeplatform.voucherOrders.service.VoucherOrderTransactionService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static com.backend.lifeplatform.common.config.SeckillRabbitMQ.QUEUE;

/**
 * 秒杀订单主队列消费者。
 *
 * <p>手动 ack 把“数据库事务结果”和“消息确认时机”绑定起来：成功或重复消息 ack，
 * 明确的业务失败拒绝并进死信，短暂系统异常先投递到延迟重试队列。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoucherOrderConsumer {

    private final VoucherOrderTransactionService voucherOrderTransactionService;

    private static final int MAX_RETRY_COUNT = 3;
    private final VoucherProducer voucherProducer;

    /** 消费一条消息，并按异常类型选择确认、重试或死信路径。 */
    @RabbitListener(queues = QUEUE)
    public void consume(
            VoucherOrderMessage orderMessage,
            Message rabbitMessage,
            Channel channel
    ) throws IOException {
        long deliveryTag = rabbitMessage.getMessageProperties().getDeliveryTag();

        log.info("received seckill order message, orderId={}, userId={}, voucherId={}",
                orderMessage.getOrderId(),
                orderMessage.getUserId(),
                orderMessage.getVoucherId());

        try {
            // 事务方法内部负责幂等、时间窗口、MySQL 条件扣库存和订单插入。
            voucherOrderTransactionService.createOrder(orderMessage.getUserId(),
                    orderMessage.getVoucherId(), orderMessage.getOrderId());

            channel.basicAck(deliveryTag, false);
        } catch (BusinessException e) {
            // 库存不足、活动结束等确定性失败无需重试，拒绝后由死信消费者做 Redis 补偿。
            log.warn(
                    "terminal seckill order failure, orderId={}, reason={}",
                    orderMessage.getOrderId(),
                    e.getMessage()
            );
            channel.basicReject(deliveryTag, false);
        } catch (DuplicateOrderMessageException duplicateOrderMessageException) {
            // 唯一索引已证明订单存在，重复消息直接 ack，避免无意义重试。
            log.info(
                    "duplicate seckill order message, orderId={}",
                    orderMessage.getOrderId()
            );

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // 数据库连接、MQ 等暂时性故障进入带 TTL 的重试队列。
            retryOrDeadLetter(
                    orderMessage,
                    rabbitMessage,
                    channel,
                    deliveryTag,
                    e);
        }
    }

    /**
     * 临时异常最多重试三次；发送重试消息成功后确认原消息，发送失败则重新入主队列。
     */
    private void retryOrDeadLetter(VoucherOrderMessage orderMessage,
                                   Message rabbitMessage,
                                   Channel channel,
                                   long deliveryTag,
                                   Exception exception)
            throws IOException {
        int retryCount = readRetryCount(rabbitMessage);

        if (retryCount >= MAX_RETRY_COUNT) {
            log.error(
                    "seckill order retries exhausted, orderId={}, retryCount={}",
                    orderMessage.getOrderId(),
                    retryCount,
                    exception
            );

            channel.basicReject(deliveryTag, false);
            return;
        }

        try {
            boolean retrySent = voucherProducer.sendRetry(
                    orderMessage,
                    retryCount + 1
            );

            if (!retrySent) {
                throw new IllegalStateException(
                        "RabbitMQ broker rejected retry message"
                );
            }

            log.warn( "seckill order scheduled for retry, orderId={}, retryCount={}",
                    orderMessage.getOrderId(),
                    retryCount + 1,
                    exception);
            channel.basicAck(deliveryTag, false);
        } catch (Exception retryPublishException) {
            log.error("retry publish failed, requeue original message, orderId={}",
                    orderMessage.getOrderId(),
                    retryPublishException
            );

            channel.basicNack(deliveryTag, false, true);
        }
    }

    private int readRetryCount(Message rabbitMessage) {
        // RabbitMQ 头部可能是 Integer、Long 等 Number，统一转换后再比较上限。
        Object value = rabbitMessage.getMessageProperties().getHeaders().get(VoucherProducer.RETRY_COUNT_HEADER);

        return value instanceof Number number ? number.intValue() : 0;
    }
}
