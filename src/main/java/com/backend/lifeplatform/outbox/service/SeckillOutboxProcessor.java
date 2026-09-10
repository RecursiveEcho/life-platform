package com.backend.lifeplatform.outbox.service;

import com.backend.lifeplatform.common.enums.ErrorCode;
import com.backend.lifeplatform.common.exception.BusinessException;
import com.backend.lifeplatform.outbox.dto.SeckillPublishPayload;
import com.backend.lifeplatform.outbox.entity.SeckillOutboxEvent;
import com.backend.lifeplatform.outbox.enums.PublishResult;
import com.backend.lifeplatform.outbox.exception.OutboxPermanentException;
import com.backend.lifeplatform.outbox.exception.OutboxRetryableException;
import com.backend.lifeplatform.voucher.service.SeckillPublishStateService;
import com.backend.lifeplatform.voucherOrders.service.SeckillRedisService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static com.backend.lifeplatform.outbox.constant.OutboxEventType.SECKILL_ACTIVITY_PUBLISH;
import static com.backend.lifeplatform.outbox.enums.PublishResult.*;

/**
 * Outbox 事件处理器。
 * <p>职责：把发件箱事件转换为对应的业务动作并执行，
 * 不直接修改 Outbox 状态，由 SeckillOutboxJob 统一推进状态机。</p>
 */

@Component
@RequiredArgsConstructor
public class SeckillOutboxProcessor {

    private final ObjectMapper objectMapper;
    private final SeckillRedisService seckillRedisService;
    private final SeckillPublishStateService seckillPublishStateService;

    public void process(SeckillOutboxEvent event, String claimToken) {

        if (event == null || claimToken == null || claimToken.isBlank()) {
            throw new OutboxPermanentException("事件或认领令牌无效");
        }
        if (Objects.equals(event.getEventType(), SECKILL_ACTIVITY_PUBLISH)) {
            try {
                if (event.getPayload() == null || event.getPayload().isBlank()) {
                    throw new OutboxPermanentException("payload 为空");
                }

                SeckillPublishPayload payload = objectMapper.readValue(
                        event.getPayload(),
                        SeckillPublishPayload.class
                );

                if (payload == null
                        || payload.getVoucherId() == null
                        || payload.getStock() == null
                        || payload.getStock() <= 0
                        || payload.getBeginTime() == null
                        || payload.getEndTime() == null
                        || !payload.getBeginTime().isBefore(payload.getEndTime())) {
                    throw new OutboxPermanentException("payload 参数缺失");
                }

                seckillRedisService.ensureActivity(
                        payload.getVoucherId(),
                        payload.getStock(),
                        payload.getBeginTime(),
                        payload.getEndTime());

                PublishResult published = seckillPublishStateService.markPublished(
                        payload.getVoucherId()
                );

                if (NOT_FOUND.equals(published)) {
                    throw new OutboxPermanentException("券不存在");
                }
                if (INVALID_STATE.equals(published)) {
                    throw new OutboxPermanentException("异常状态操作失败");
                }

                /*
                 * 先让 MySQL 进入 PUBLISHED，再激活 Redis。
                 * 秒杀消费者会再次检查 MySQL 状态；如果先激活 Redis，
                 * 就会出现 Redis 已可抢但 MySQL 仍是 PUBLISHING 的窗口，
                 * 订单消息会被消费者拒绝。当前顺序选择“暂时不可抢”而不是“接单后失败”。
                 */
                seckillRedisService.activateActivity(payload.getVoucherId());
            } catch (JsonProcessingException e) {
                throw new OutboxPermanentException("JSON 解析失败", e);
            } catch (RedisException | RedisConnectionFailureException | RedisSystemException e) {
                throw new OutboxRetryableException("Redis 临时连接失败", e);
            } catch (DataAccessException e) {
                throw new OutboxRetryableException("数据库临时错" +
                        "误", e);
            } catch (BusinessException e) {
                if (e.getCode() == ErrorCode.CACHE_ERROR.getCode()) {
                    throw new OutboxRetryableException("Redis 缓存操作失败", e);
                }
                throw new OutboxPermanentException("事件业务参数无效", e);
            }
        } else {
            throw new OutboxPermanentException("业务类型未知");
        }
    }
}
