package com.backend.lifeplatform.voucher.service;

import com.backend.lifeplatform.outbox.enums.PublishResult;
import com.backend.lifeplatform.voucher.constant.SeckillActivityStatus;
import com.backend.lifeplatform.voucher.entity.SeckillVouchers;
import com.backend.lifeplatform.voucher.mapper.SeckillVouchersMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.backend.lifeplatform.outbox.enums.PublishResult.*;

/**
 * 秒杀活动发布状态服务。
 * <p>只负责在 Outbox 事件执行成功后，独立提交活动的最终发布状态。</p>
 */
@Service
@RequiredArgsConstructor
public class SeckillPublishStateService {

    private final SeckillVouchersMapper seckillVouchersMapper;

    /**
     * 把活动置为「已发布」，并区分本次更新、幂等已完成和状态冲突。
     */
    @Transactional
    public PublishResult markPublished(Long voucherId) {
        if (seckillVouchersMapper.markPublished(voucherId) == 1) {
            return PUBLISHED_NOW;
        } else {
            SeckillVouchers seckillVouchers = seckillVouchersMapper.selectById(voucherId);
            if (seckillVouchers == null) {
                return NOT_FOUND;
            } else if (SeckillActivityStatus.PUBLISHED.equals(seckillVouchers.getStatus())) {
                return ALREADY_PUBLISHED;
            } else {
                return INVALID_STATE;
            }
        }
    }
}
