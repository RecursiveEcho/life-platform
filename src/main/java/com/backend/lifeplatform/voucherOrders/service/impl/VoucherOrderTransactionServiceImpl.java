package com.backend.lifeplatform.voucherOrders.service.impl;

import com.backend.lifeplatform.common.enums.ErrorCode;
import com.backend.lifeplatform.common.exception.BusinessException;
import com.backend.lifeplatform.voucher.constant.SeckillActivityStatus;
import com.backend.lifeplatform.voucher.entity.SeckillVouchers;
import com.backend.lifeplatform.voucher.mapper.SeckillVouchersMapper;
import com.backend.lifeplatform.voucherOrders.entity.VoucherOrders;
import com.backend.lifeplatform.voucherOrders.exception.DuplicateOrderMessageException;
import com.backend.lifeplatform.voucherOrders.mapper.VoucherOrdersMapper;
import com.backend.lifeplatform.voucherOrders.service.VoucherOrderTransactionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 消费秒杀消息时执行的数据库事务。
 *
 * <p>Redis 预扣只是入口层的并发保护，数据库事务仍然必须再次检查时间、库存和唯一订单，
 * 防止消息重复、延迟或人工补发时绕过最终一致性约束。</p>
 */
@Service
@RequiredArgsConstructor
public class VoucherOrderTransactionServiceImpl implements VoucherOrderTransactionService {

    private final SeckillVouchersMapper seckillVouchersMapper;
    private final VoucherOrdersMapper voucherOrdersMapper;

    /** 在一个 MySQL 事务中完成秒杀订单落库，返回 false 表示重复消息。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createOrder(Long userId, Long voucherId, Long orderId) {

        /*
         * 第一步：幂等检查。
         *
         * 如果同一个用户已经购买过同一张券，
         * 说明这可能是重复消息，直接返回 false。
         */
        VoucherOrders existingOrders = voucherOrdersMapper.selectOne(new LambdaQueryWrapper<VoucherOrders>()
                .eq(VoucherOrders::getVoucherId, voucherId)
                .eq(VoucherOrders::getUserId, userId)
        );

        if (existingOrders != null) {
            return false;
        }

        /*
         * 第二步：查询秒杀券。
         */

        SeckillVouchers seckillVouchers = seckillVouchersMapper.selectById(voucherId);

        if (seckillVouchers == null) {
            throw new BusinessException(ErrorCode.VOUCHER_NOT_FOUND, "优惠卷不存在");
        }

        if (SeckillActivityStatus.PUBLISHING.equals(seckillVouchers.getStatus())) {
            throw new IllegalStateException("秒杀活动正在发布");
        }

        if (!SeckillActivityStatus.PUBLISHED.equals(seckillVouchers.getStatus())) {
            throw new BusinessException(
                    ErrorCode.VOUCHER_NOT_AVAILABLE,
                    "秒杀活动未发布或已下线"
            );
        }

        /*
         * 第三步：校验秒杀时间。
         */
        LocalDateTime now = LocalDateTime.now();

        if (now.isBefore(seckillVouchers.getBeginTime()) || now.isAfter(seckillVouchers.getEndTime())) {
            throw new BusinessException(ErrorCode.SECKILL_OUT_OF_TIME,
                    "不在秒杀时间范围内");
        }

        /*
         * 第四步：原子扣减 MySQL 库存。
         *
         * SQL 中必须带 stock > 0。
         * 只有受影响行数为 1，才代表扣库存成功。
         */
        int affectedRows =
                seckillVouchersMapper.decrementUpdate(voucherId);

        if (affectedRows != 1) {
            throw new BusinessException(
                    ErrorCode.VOUCHER_SOLD_OUT,
                    "秒杀券已抢光"
            );
        }

        /*
         * 第五步：创建订单。
         */
        VoucherOrders order = new VoucherOrders();

        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setOrderStatus(1);

        try {
            int insertRows = voucherOrdersMapper.insert(order);

            if (insertRows != 1) {
                throw new BusinessException(
                        ErrorCode.ORDER_CREATE_FAILED,
                        "创建订单失败"
                );
            }
        } catch (DuplicateKeyException exception) {
            /*
             * 并发情况下，两个线程可能都通过了前面的查询，
             * 但只有一个线程能插入成功。
             *
             * 唯一索引会阻止第二个订单插入。
             * 异常继续抛出，让当前事务回滚库存扣减。
             */
            throw new DuplicateOrderMessageException(exception);
        }

        return true;
    }
}
