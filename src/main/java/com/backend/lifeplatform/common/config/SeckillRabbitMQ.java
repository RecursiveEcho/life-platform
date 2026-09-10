package com.backend.lifeplatform.common.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 秒杀下单的 RabbitMQ 交换机 / 队列声明。
 * <p>
 * 作用：把「用户抢券」从同步请求中解耦。Controller 只负责扣减库存并把消息投递到该队列，
 * 订单由消费者（见 voucherOrders 模块）异步创建，从而支撑高并发秒杀场景、削峰填谷。
 */
@Configuration
public class SeckillRabbitMQ {

    /**
     * 秒杀订单队列名。
     */
    public static final String QUEUE = "seckill.order.queue";
    /**
     * 秒杀订单直连交换机名。
     */
    public static final String EXCHANGE = "seckill.order.exchange";
    /**
     * 队列绑定到交换机的路由键。
     */
    public static final String ROUTING_KEY = "seckill.order";
    /** 最终失败消息使用的死信交换机、队列和路由键。 */
    public static final String DEAD_LETTER_EXCHANGE = "seckill.dlx";
    public static final String DEAD_LETTER_QUEUE = "seckill.dlq";
    public static final String DEAD_LETTER_ROUTING_KEY = "seckill.order.dead";
    /** 临时失败消息使用的重试交换机、队列和路由键。 */
    public static final String RETRY_EXCHANGE = "seckill.order.retry.exchange";
    public static final String RETRY_QUEUE = "seckill.order.retry.queue";
    public static final String RETRY_ROUTING_KEY = "seckill.order.retry";
    /** 重试队列中的消息等待该时长后通过死信配置回到主队列。 */
    public static final int RETRY_DELAY_MILLIS = 10_000;

    /** 声明死信交换机：持久化，不自动删除。 */
    @Bean
    public DirectExchange seckillOrderDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    /** 最终失败消息进入的持久化队列，由补偿消费者处理。 */
    @Bean
    public Queue seckillOrderDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    /** 将死信交换机按专用路由键绑定到死信队列。 */
    @Bean
    public Binding seckillOrderDeadLetterBinding(@Qualifier("seckillOrderDeadLetterQueue") Queue seckillOrderDeadLetterQueue, @Qualifier("seckillOrderDeadLetterExchange") DirectExchange seckillOrderDeadLetterExchange) {
        return BindingBuilder.bind(seckillOrderDeadLetterQueue).to(seckillOrderDeadLetterExchange).with(DEAD_LETTER_ROUTING_KEY);
    }

    /** 临时失败的订单先在此等待，TTL 到期后自动回流到正常订单队列。 */
    @Bean
    public DirectExchange seckillOrderRetryExchange() {
        return new DirectExchange(RETRY_EXCHANGE, true, false);
    }

    /** 声明带 TTL 的重试队列，过期消息通过死信配置回到主交换机。 */
    @Bean
    public Queue seckillOrderRetryQueue() {
        return QueueBuilder.durable(RETRY_QUEUE)
                .ttl(RETRY_DELAY_MILLIS)
                .deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey(ROUTING_KEY)
                .build();
    }

    /** 将延迟重试交换机按重试路由键绑定到重试队列。 */
    @Bean
    public Binding seckillOrderRetryBinding(
            @Qualifier("seckillOrderRetryQueue") Queue seckillOrderRetryQueue,
            @Qualifier("seckillOrderRetryExchange") DirectExchange seckillOrderRetryExchange) {
        return BindingBuilder.bind(seckillOrderRetryQueue)
                .to(seckillOrderRetryExchange)
                .with(RETRY_ROUTING_KEY);
    }

    /**
     * 声明直连交换机：持久化（重启后交换机仍在），不自动删除。
     */
    @Bean
    public DirectExchange seckillOrderExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    /**
     * 声明持久化队列，保证 MQ 重启后尚未消费的消息不丢失。
     */
    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder.durable(QUEUE).deadLetterExchange(DEAD_LETTER_EXCHANGE).deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY).build();
    }

    /**
     * 把队列按路由键绑定到交换机，投递到该键的消息才会进入队列。
     */
    @Bean
    public Binding seckillOrderBinding(@Qualifier("seckillOrderExchange") DirectExchange directExchange, @Qualifier("seckillOrderQueue") Queue queue) {
        return BindingBuilder.bind(queue).to(directExchange).with(ROUTING_KEY);
    }
}
