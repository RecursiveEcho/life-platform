package com.backend.lifeplatform.voucherOrders.mq;

import com.backend.lifeplatform.common.enums.ErrorCode;
import com.backend.lifeplatform.common.exception.BusinessException;
import com.backend.lifeplatform.voucherOrders.exception.DuplicateOrderMessageException;
import com.backend.lifeplatform.voucherOrders.service.VoucherOrderTransactionService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link VoucherOrderConsumer} 的单元测试。
 *
 * <p>覆盖手动 ack 的四条分支：成功确认、确定性失败拒绝、重复消息确认、
 * 临时异常按重试次数进入 TTL 重试队列或死信队列。全部用 Mockito 模拟，
 * 不需要真实 MySQL / Redis / RabbitMQ。</p>
 */
@ExtendWith(MockitoExtension.class)
class VoucherOrderConsumerTest {

    private static final long DELIVERY_TAG = 42L;
    private static final Long USER_ID = 1001L;
    private static final Long VOUCHER_ID = 2002L;
    private static final Long ORDER_ID = 3003L;

    @Mock
    private VoucherOrderTransactionService transactionService;

    @Mock
    private VoucherProducer voucherProducer;

    @Mock
    private Channel channel;

    private VoucherOrderConsumer consumer;
    private VoucherOrderMessage orderMessage;

    @BeforeEach
    void setUp() {
        consumer = new VoucherOrderConsumer(transactionService, voucherProducer);
        orderMessage = new VoucherOrderMessage(USER_ID, VOUCHER_ID, ORDER_ID);
    }

    /** 构造带指定重试次数的 RabbitMQ 消息；retryCount 为 null 表示首次投递。 */
    private Message messageWithRetryCount(Object retryCount) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(DELIVERY_TAG);
        if (retryCount != null) {
            properties.setHeader(VoucherProducer.RETRY_COUNT_HEADER, retryCount);
        }
        return new Message(new byte[0], properties);
    }

    private void givenCreateOrderThrows(RuntimeException exception) {
        when(transactionService.createOrder(anyLong(), anyLong(), anyLong())).thenThrow(exception);
    }

    @Test
    void consume_whenOrderCreated_acksMessage() throws Exception {
        when(transactionService.createOrder(USER_ID, VOUCHER_ID, ORDER_ID)).thenReturn(true);

        consumer.consume(orderMessage, messageWithRetryCount(null), channel);

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        verify(voucherProducer, never()).sendRetry(any(), anyInt());
    }

    @Test
    void consume_whenBusinessFails_rejectsWithoutRetry() throws Exception {
        givenCreateOrderThrows(new BusinessException(ErrorCode.VOUCHER_SOLD_OUT, "秒杀券已抢光"));

        consumer.consume(orderMessage, messageWithRetryCount(null), channel);

        verify(channel).basicReject(DELIVERY_TAG, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(voucherProducer, never()).sendRetry(any(), anyInt());
    }

    @Test
    void consume_whenMessageIsDuplicate_acksWithoutRetry() throws Exception {
        givenCreateOrderThrows(
                new DuplicateOrderMessageException(new RuntimeException("duplicate key")));

        consumer.consume(orderMessage, messageWithRetryCount(null), channel);

        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
        verify(voucherProducer, never()).sendRetry(any(), anyInt());
    }

    @Test
    void consume_whenTemporaryFailure_sendsRetryAndAcksOriginal() throws Exception {
        givenCreateOrderThrows(new IllegalStateException("database temporarily unavailable"));
        when(voucherProducer.sendRetry(any(), eq(1))).thenReturn(true);

        consumer.consume(orderMessage, messageWithRetryCount(null), channel);

        verify(voucherProducer).sendRetry(orderMessage, 1);
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicReject(anyLong(), anyBoolean());
    }

    @Test
    void consume_whenRetryCountHeaderIsLong_readsItAndSendsNextRetry() throws Exception {
        givenCreateOrderThrows(new IllegalStateException("database temporarily unavailable"));
        when(voucherProducer.sendRetry(any(), eq(3))).thenReturn(true);

        consumer.consume(orderMessage, messageWithRetryCount(2L), channel);

        verify(voucherProducer).sendRetry(orderMessage, 3);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void consume_whenRetriesExhausted_rejectsWithoutRetry() throws Exception {
        givenCreateOrderThrows(new IllegalStateException("database temporarily unavailable"));

        consumer.consume(orderMessage, messageWithRetryCount(3), channel);

        verify(voucherProducer, never()).sendRetry(any(), anyInt());
        verify(channel).basicReject(DELIVERY_TAG, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void consume_whenBrokerRejectsRetryMessage_requeuesOriginal() throws Exception {
        givenCreateOrderThrows(new IllegalStateException("database temporarily unavailable"));
        when(voucherProducer.sendRetry(any(), eq(1))).thenReturn(false);

        consumer.consume(orderMessage, messageWithRetryCount(null), channel);

        verify(channel).basicNack(DELIVERY_TAG, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void consume_whenRetryPublishThrows_requeuesOriginal() throws Exception {
        givenCreateOrderThrows(new IllegalStateException("database temporarily unavailable"));
        when(voucherProducer.sendRetry(any(), anyInt()))
                .thenThrow(new IOException("broker unavailable"));

        consumer.consume(orderMessage, messageWithRetryCount(null), channel);

        verify(channel).basicNack(DELIVERY_TAG, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
}
