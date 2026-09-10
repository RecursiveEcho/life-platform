package com.backend.lifeplatform.outbox.service;

import com.backend.lifeplatform.outbox.constant.OutboxEventStatus;
import com.backend.lifeplatform.outbox.constant.OutboxEventType;
import com.backend.lifeplatform.outbox.entity.SeckillOutboxEvent;
import com.backend.lifeplatform.outbox.mapper.SeckillOutboxEventMapper;
import com.backend.lifeplatform.voucher.entity.SeckillVouchers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * 秒杀 Outbox 事件服务。
 * <p>在业务事务内写入「秒杀活动发布」事件，等待定时任务扫描后投递到 MQ，
 * 用发件箱模式保证发布动作不会因本地提交成功但消息丢失而漏发。</p>
 */
@Service
@RequiredArgsConstructor
public class SeckillOutboxService {

    private final SeckillOutboxEventMapper mapper;
    private final ObjectMapper objectMapper;


    /**
     * 创建一条「秒杀活动发布」事件并落库，负载里打包券 id、库存与起止时间，
     * 投递时无需再回查数据库；是否随事务提交由调用方的事务边界决定。
     */
    public void createPublishEvent(SeckillVouchers activity) {
        SeckillOutboxEvent event = new SeckillOutboxEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType(OutboxEventType.SECKILL_ACTIVITY_PUBLISH);
        event.setAggregateId(activity.getVoucherId());
        // 把发布所需参数打包成 JSON 存入负载，投递时无需再回查数据库
        try {
            event.setPayload(objectMapper.writeValueAsString(Map.of(
                    "voucherId", activity.getVoucherId(),
                    "stock", activity.getStock(),
                    "beginTime", activity.getBeginTime(),
                    "endTime", activity.getEndTime()
            )));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("秒杀发布事件序列化失败", e);
        }
        event.setStatus(OutboxEventStatus.NEW);
        event.setRetryCount(0);
        mapper.insert(event);
    }
}
