package com.backend.lifeplatform.voucherOrders.mq;

import com.backend.lifeplatform.voucherOrders.service.SeckillRedisService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link VoucherOrderDeadLetterConsumer} 的单元测试。
 *
 * <p>死信只做一次可幂等的 Redis 预扣补偿：补偿成功确认消息，补偿失败重新入队。
 * 用 Mockito 模拟 Redis 补偿服务，不需要真实 Redis。</p>
 */
@ExtendWith(MockitoExtension.class)
class VoucherOrderDeadLetterConsumerTest {

    private static final long DELIVERY_TAG = 7L;
    private static final Long USER_ID = 1001L;
    private static final Long VOUCHER_ID = 2002L;
    private static final Long ORDER_ID = 3003L;

    @Mock
    private SeckillRedisService seckillRedisService;

    @Mock
    private Channel channel;

    private VoucherOrderDeadLetterConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new VoucherOrderDeadLetterConsumer(seckillRedisService);
    }

    private Message deadLetterMessage() {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(DELIVERY_TAG);
        return new Message(new byte[0], properties);
    }

    @Test
    void consume_whenCompensationSucceeds_acksDeadLetter() throws IOException {
        consumer.consume(
                new VoucherOrderMessage(USER_ID, VOUCHER_ID, ORDER_ID),
                deadLetterMessage(),
                channel);

        // 补偿顺序是 (voucherId, userId)
        verify(seckillRedisService).compensatePreDeduct(VOUCHER_ID, USER_ID);
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(anyLong(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void consume_whenCompensationFails_requeuesDeadLetter() throws IOException {
        doThrow(new IllegalStateException("redis unavailable"))
                .when(seckillRedisService)
                .compensatePreDeduct(anyLong(), anyLong());

        consumer.consume(
                new VoucherOrderMessage(USER_ID, VOUCHER_ID, ORDER_ID),
                deadLetterMessage(),
                channel);

        verify(channel).basicNack(DELIVERY_TAG, false, true);
        verify(channel, never()).basicAck(anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
    }
}
