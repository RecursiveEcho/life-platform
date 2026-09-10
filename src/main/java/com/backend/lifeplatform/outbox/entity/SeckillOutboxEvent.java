package com.backend.lifeplatform.outbox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀 Outbox 事件实体，对应表 seckill_outbox_event。
 * <p>事务性发件箱（Transactional Outbox）：在同一数据库事务里，业务数据变更和该事件
 * 一起落库，之后由定时任务投递到 MQ，保证「本地事务提交」与「消息发送」的一致性。</p>
 */
@Data
@TableName("seckill_outbox_event")
public class SeckillOutboxEvent {

    /** 主键 ID，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件唯一标识（UUID），用于去重和日志追踪 */
    private String eventId;

    /** 事件类型，见 OutboxEventType */
    private String eventType;

    /** 聚合根 ID（本场景为秒杀券 id），便于按业务对象定位事件 */
    private Long aggregateId;

    /** 事件负载 JSON，存发布秒杀活动所需的参数 */
    private String payload;

    /** 事件状态，见 OutboxEventStatus */
    private String status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 下次重试时间（失败后退避，由定时任务据此捞取） */
    private LocalDateTime nextRetryTime;

    /** 最近一次投递失败的错误信息 */
    private String lastError;

    private String claimToken;

    private LocalDateTime leaseUntil;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
