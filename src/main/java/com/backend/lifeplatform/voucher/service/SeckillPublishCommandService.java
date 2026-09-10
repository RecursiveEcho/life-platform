package com.backend.lifeplatform.voucher.service;

import com.backend.lifeplatform.common.enums.ErrorCode;
import com.backend.lifeplatform.common.exception.BusinessException;
import com.backend.lifeplatform.outbox.service.SeckillOutboxService;
import com.backend.lifeplatform.voucher.constant.SeckillActivityStatus;
import com.backend.lifeplatform.voucher.entity.SeckillVouchers;
import com.backend.lifeplatform.voucher.mapper.SeckillVouchersMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 秒杀活动发布命令服务。
 * <p>发布分两步：先把草稿活动置为「发布中」，再写发件箱事件；
 * 两者在同一事务内完成，Redis 初始化与上线交给 outbox 定时任务异步执行。</p>
 */
@Service
@RequiredArgsConstructor
public class SeckillPublishCommandService {

    private final SeckillVouchersMapper seckillVouchersMapper;
    private final SeckillOutboxService seckillOutboxService;

    /**
     * 启动发布流程：校验草稿状态 → 状态置为发布中 → 写发件箱事件。
     * 整个过程在一个事务内，任一步失败一起回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public void startPublish(Long voucherId){
            SeckillVouchers activity = seckillVouchersMapper.selectById(voucherId);

            if(activity==null||!SeckillActivityStatus.DRAFT.equals(activity.getStatus())){
                throw new BusinessException(ErrorCode.INVALID_OPERATION,"操作失败");
            }

            int marked =seckillVouchersMapper.markPublishing(voucherId);

            if(marked==0){
                throw new BusinessException(
                        ErrorCode.INVALID_OPERATION,
                        "秒杀活动不是草稿状态，无法发布"
                );
            }

            seckillOutboxService.createPublishEvent(activity);
        }
}
