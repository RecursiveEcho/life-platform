package com.backend.lifeplatform.outbox.job;

import com.backend.lifeplatform.outbox.constant.OutboxEventStatus;
import com.backend.lifeplatform.outbox.entity.SeckillOutboxEvent;
import com.backend.lifeplatform.outbox.exception.OutboxPermanentException;
import com.backend.lifeplatform.outbox.exception.OutboxRetryableException;
import com.backend.lifeplatform.outbox.mapper.SeckillOutboxEventMapper;
import com.backend.lifeplatform.outbox.service.SeckillOutboxProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Outbox 定时任务：周期扫描待处理事件并投递。
 * <p>发件箱事件的投递由本任务驱动，配合 SeckillOutboxProcessor 完成
 * 「捞取 → 认领 → 投递 → 确认」的闭环。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOutboxJob {

    /**
     * 每次最多捞取并处理的事件数量，避免单次扫描长时间占用资源。
     */
    private static final int BATCH_SIZE = 20;

    @Value("${seckill.outbox-lease-seconds:60}")
    private long outboxLeaseSeconds;
    @Value("${seckill.outbox.max-retry-count:3}")
    private int maxRetryCount;
    @Value("${seckill.outbox.retry-delay-seconds:10}")
    private int retryDelaySeconds;

    private final SeckillOutboxEventMapper seckillOutboxEventMapper;
    private final SeckillOutboxProcessor seckillOutboxProcessor;

    /**
     * 周期扫描 NEW 与到期的 RETRY_WAIT 事件，逐个尝试认领并处理。
     */
    @Scheduled(fixedDelayString = "${seckill.outbox-delay-ms:5000}")
    public void processPendingEvents() {
        List<SeckillOutboxEvent> events = seckillOutboxEventMapper.findPendingEvents(
                OutboxEventStatus.NEW,
                OutboxEventStatus.RETRY_WAIT,
                OutboxEventStatus.PROCESSING,
                BATCH_SIZE
        );

        for (SeckillOutboxEvent event : events) {
            try {
                tryClaimAndProcess(event);
            } catch (Exception e) {
                /*
                 * 单条事件的异常不能中断同一批次的其他事件。
                 * 如果事件已经被认领但状态更新失败，租约到期后会被下一轮扫描重新认领。
                 */
                log.error(
                        "Outbox event processing failed unexpectedly, eventId={}",
                        event.getEventId(),
                        e
                );
            }
        }
    }

    /**
     * 原子认领单条事件：认领失败说明被其他 worker 抢先，直接跳过；
     * 认领成功后的实际业务处理交由 SeckillOutboxProcessor 完成。
     */
    private void tryClaimAndProcess(SeckillOutboxEvent event) {

        String claimToken = UUID.randomUUID().toString();
        LocalDateTime leaseUntil = LocalDateTime.now().plusSeconds(outboxLeaseSeconds);

        int claimed = seckillOutboxEventMapper.claimEvent(event.getId(), claimToken, leaseUntil);

        if (claimed != 1) {
            log.debug(
                    "Outbox event was claimed by another worker, eventId={}",
                    event.getEventId()
            );
            return;
        }

        int currentRetryCount = event.getRetryCount() == null ? 0 : event.getRetryCount();

        try {
            seckillOutboxProcessor.process(event, claimToken);
            int affectedRows = seckillOutboxEventMapper.markSuccess(
                    event.getId(),
                    claimToken
            );

            if (affectedRows != 1) {
                log.warn(
                        "Outbox success update lost ownership, eventId={}",
                        event.getEventId()
                );
                return;
            }
        } catch (OutboxPermanentException e) {
            int affectedRows = seckillOutboxEventMapper.markFailed(
                    event.getId(),
                    currentRetryCount,
                    e.getMessage(),
                    claimToken
            );

            if (affectedRows != 1) {
                log.warn(
                        "Outbox failed update lost ownership, eventId={}",
                        event.getEventId()
                );
                return;
            }

        } catch (OutboxRetryableException e) {
            handleRetryableFailure(event, currentRetryCount, e.getMessage(), claimToken);
        } catch (Exception e) {
            /*
             * 未被明确分类的异常按临时故障处理，避免事件停留在 PROCESSING
             * 后只靠租约反复重跑且永远不增加 retry_count。
             */
            handleRetryableFailure(event, currentRetryCount, "未分类处理异常", claimToken);
        }

        log.info(
                "Outbox event processed, eventId={}, eventType={}",
                event.getEventId(),
                event.getEventType()
        );
    }

    /**
     * 统一推进临时失败状态：先增加失败次数，再决定等待重试还是最终失败。
     */
    private void handleRetryableFailure(
            SeckillOutboxEvent event,
            int currentRetryCount,
            String lastError,
            String claimToken
    ) {
        int nextRetryCount = currentRetryCount + 1;
        if (nextRetryCount > maxRetryCount) {
            int affectedRows = seckillOutboxEventMapper.markFailed(
                    event.getId(),
                    nextRetryCount,
                    lastError,
                    claimToken
            );

            if (affectedRows != 1) {
                log.warn(
                        "Outbox retry exhaustion update lost ownership, eventId={}",
                        event.getEventId()
                );
            }
            return;
        }

        LocalDateTime nextRetryTime = LocalDateTime.now()
                .plusSeconds((long) retryDelaySeconds * nextRetryCount);
        int affectedRows = seckillOutboxEventMapper.markRetry(
                event.getId(),
                nextRetryCount,
                nextRetryTime,
                lastError,
                claimToken
        );

        if (affectedRows != 1) {
            log.warn(
                    "Outbox retry update lost ownership, eventId={}",
                    event.getEventId()
            );
        }
    }
}
