package com.backend.lifeplatform.voucherOrders.mq;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.backend.lifeplatform.common.config.SeckillRabbitMQ.*;

/**
 * 秒杀订单消息生产者。
 *
 * <p>普通投递使用主交换机；临时失败的消息带重试次数进入延迟队列，
 * 超过次数后由消费者拒绝并进入死信队列。</p>
 */
@Component
@RequiredArgsConstructor
public class VoucherProducer {

    private final RabbitTemplate rabbitTemplate;

    public static final String RETRY_COUNT_HEADER = "x-retry-count";

    /** 普通发送入口，适用于不需要同步等待 broker 确认的场景。 */
    public void send(VoucherOrderMessage voucherOrderMessage) {
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
        rabbitTemplate.convertAndSend(EXCHANGE,
                ROUTING_KEY,
                voucherOrderMessage,
                correlationData
        );
    }

    /** 发布临时失败消息，并等待 broker 确认，避免原消息确认后重试消息其实没有发出。 */
    public boolean sendRetry(VoucherOrderMessage orderMessage, int retryCount) throws Exception {
        // 重试次数放在消息头，不修改业务消息体，消费者可据此决定是否转死信。
        MessagePostProcessor retryHeader = message -> {
            message.getMessageProperties().setHeader(RETRY_COUNT_HEADER, retryCount);
            return message;
        };

        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());

        rabbitTemplate.convertAndSend(
                RETRY_EXCHANGE, RETRY_ROUTING_KEY, orderMessage, retryHeader, correlationData);

        CorrelationData.Confirm confirm = correlationData.getFuture().get(5, TimeUnit.SECONDS);

        return confirm.isAck();
    }

    /** 发布主队列消息并同步等待 broker confirm，供 HTTP 入口决定是否补偿 Redis。 */
    public boolean sendAndConfirm(VoucherOrderMessage message) throws Exception {
        // HTTP 线程只有在 broker 确认接收后才返回订单号；确认失败时上层会执行 Redis 补偿。
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());

        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, message, correlationData);

        CorrelationData.Confirm confirm = correlationData.getFuture().get(5, TimeUnit.SECONDS);

        return confirm.isAck();
    }
}
