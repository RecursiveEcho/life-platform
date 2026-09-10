package com.backend.lifeplatform.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** RabbitMQ JSON 消息转换和发布确认配置。 */
@Configuration
@Slf4j
public class RabbitPublishConverter {

    /** 让订单消息以 JSON 编码发布，消费者可直接反序列化为 VoucherOrderMessage。 */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /** 记录 broker 确认和不可路由消息，便于区分“发送成功”和“消息到达队列”。 */
    @Bean
    public RabbitTemplateCustomizer rabbitTemplateCustomizer() {
        return rabbitTemplate -> {
            rabbitTemplate.setConfirmCallback(
                    (correlationData, ack, cause) -> {
                        String messageId = correlationData == null
                                ? "unknown" : correlationData.getId();

                        if (ack) {
                            log.info("RabbitMQ broker confirmed message, messageId={}", messageId);
                        } else {
                            log.error("RabbitMQ broker rejected message, messageId={}, cause={}",
                                    messageId, cause);
                        }
                    }
            );

            rabbitTemplate.setReturnsCallback(returnedMessage -> log.error(
                    "RabbitMQ message returned, exchange={}, routingKey={}, replyCode={}, replyText={}"
                    , returnedMessage.getExchange(),
                    returnedMessage.getRoutingKey(),
                    returnedMessage.getReplyCode(),
                    returnedMessage.getReplyText()));
        };
    }
}
