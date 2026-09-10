package com.backend.lifeplatform.outbox.mapper;

import com.backend.lifeplatform.outbox.entity.SeckillOutboxEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀 Outbox 事件数据访问层。
 */
@Mapper
public interface SeckillOutboxEventMapper extends BaseMapper<SeckillOutboxEvent> {

    /**
     * 分页捞取待处理事件：状态为 NEW，或状态为 RETRY_WAIT 且已到重试时间的记录。
     */
    @Select("""
              select * from life_platform.seckill_outbox_event
              where  
              status = #{statusNew}
            OR (
                status = #{retryWaitStatus}
                AND (
                    next_retry_time IS NULL
                    OR next_retry_time <= NOW()
                )
            )
            OR (
                status = #{processingStatus}
                and(
                    seckill_outbox_event.lease_until IS NULL  OR
                    seckill_outbox_event.lease_until <= now()
                )
            )
              ORDER BY id 
              limit #{limit}
            """)
    List<SeckillOutboxEvent> findPendingEvents(
            @Param("statusNew") String statusNew,
            @Param("retryWaitStatus") String retryWaitStatus,
            @Param("processingStatus") String processingStatus,
            @Param("limit") int limit
    );

    /**
     * 原子认领事件：把待处理状态改为 PROCESSING，条件里带原状态防止并发重复认领。
     * 返回受影响行数，0 表示已被其他线程抢先认领。
     */
    @Update("""
            UPDATE seckill_outbox_event
            SET status = 'PROCESSING',
                update_time = NOW(),
                claim_token = #{claimToken},
                lease_until = #{leaseUntil}
            WHERE id = #{id}
              AND (
                    status = 'NEW'
                    OR (
                        status = 'RETRY_WAIT'
                        AND (
                            next_retry_time IS NULL
                            OR next_retry_time <= NOW()
                        )
                    )
                    OR (
                        status = 'PROCESSING'
                        AND (
                            lease_until IS NULL
                            OR lease_until <= NOW()
                        )
                    )
                  )
            """)
    int claimEvent(
            @Param("id") Long id,
            @Param("claimToken") String claimToken,
            @Param("leaseUntil") LocalDateTime leaseUntil
    );

    /**
     * 投递成功后把事件置为 SUCCESS 并清空错误信息。
     */
    @Update("""
                update life_platform.seckill_outbox_event
                set seckill_outbox_event.status = 'SUCCESS',
                    seckill_outbox_event.last_error = null,
                    seckill_outbox_event.update_time = NOW(),
                    claim_token = NULL,
                    lease_until = NULL
                where id = #{id}
                    and seckill_outbox_event.status = 'PROCESSING'
                    and claim_token = #{claimToken}
            """)
    int markSuccess(@Param("id") Long id, @Param("claimToken") String claimToken);

    /**
     * 投递失败后记录重试次数与下次重试时间，进入 RETRY_WAIT 等待重新调度。
     */
    @Update("""
            update life_platform.seckill_outbox_event
            set seckill_outbox_event.status = 'RETRY_WAIT',
                seckill_outbox_event.update_time = NOW(),
                seckill_outbox_event.retry_count = #{retryCount},
                seckill_outbox_event.next_retry_time = #{nextRetryTime},
                seckill_outbox_event.last_error = #{lastError},
                claim_token = NULL,
                lease_until = NULL
            where seckill_outbox_event.id = #{id} and seckill_outbox_event.status = 'PROCESSING'
             and claim_token = #{claimToken}
            """)
    int markRetry(
            @Param("id") Long id,
            @Param("retryCount") int retryCount,
            @Param("nextRetryTime") LocalDateTime nextRetryTime,
            @Param("lastError") String lastError,
            @Param("claimToken") String claimToken
    );

    /**
     * 把当前持有租约的事件置为 FAILED，等待人工处理。
     * 永久性错误和重试次数耗尽都走这里。
     */
    @Update("""
            update life_platform.seckill_outbox_event
            set seckill_outbox_event.status = 'FAILED',
                seckill_outbox_event.update_time = NOW(),
                seckill_outbox_event.retry_count = #{retryCount},
                seckill_outbox_event.last_error = #{lastError},
                claim_token = NULL,
                lease_until = NULL
            where seckill_outbox_event.id = #{id}
              and seckill_outbox_event.status = 'PROCESSING'
              and claim_token = #{claimToken}
            """)
    int markFailed(
            @Param("id") Long id,
            @Param("retryCount") int retryCount,
            @Param("lastError") String lastError,
            @Param("claimToken") String claimToken
    );
}
