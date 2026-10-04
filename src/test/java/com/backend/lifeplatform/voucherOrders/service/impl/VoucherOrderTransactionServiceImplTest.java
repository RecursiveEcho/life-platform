package com.backend.lifeplatform.voucherOrders.service.impl;

import com.backend.lifeplatform.common.enums.ErrorCode;
import com.backend.lifeplatform.common.exception.BusinessException;
import com.backend.lifeplatform.voucher.constant.SeckillActivityStatus;
import com.backend.lifeplatform.voucher.entity.SeckillVouchers;
import com.backend.lifeplatform.voucher.mapper.SeckillVouchersMapper;
import com.backend.lifeplatform.voucherOrders.entity.VoucherOrders;
import com.backend.lifeplatform.voucherOrders.exception.DuplicateOrderMessageException;
import com.backend.lifeplatform.voucherOrders.mapper.VoucherOrdersMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link VoucherOrderTransactionServiceImpl} 的单元测试。
 *
 * <p>重点覆盖「重复订单」相关的幂等与并发兜底：已有订单直接返回 false、
 * 唯一索引冲突转成 {@link DuplicateOrderMessageException} 让事务回滚，
 * 以及库存在 SQL 层被扣光的情形。全部用 Mockito 模拟 Mapper，不需要真实 MySQL。</p>
 */
@ExtendWith(MockitoExtension.class)
class VoucherOrderTransactionServiceImplTest {

    private static final Long USER_ID = 1001L;
    private static final Long VOUCHER_ID = 2002L;
    private static final Long ORDER_ID = 3003L;

    @Mock
    private SeckillVouchersMapper seckillVouchersMapper;

    @Mock
    private VoucherOrdersMapper voucherOrdersMapper;

    private VoucherOrderTransactionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VoucherOrderTransactionServiceImpl(seckillVouchersMapper, voucherOrdersMapper);
    }

    /** 一张处于已发布状态、且当前在秒杀时间窗内的券。 */
    private SeckillVouchers publishedVoucher() {
        SeckillVouchers voucher = new SeckillVouchers();
        voucher.setVoucherId(VOUCHER_ID);
        voucher.setStock(10);
        voucher.setStatus(SeckillActivityStatus.PUBLISHED);
        voucher.setBeginTime(LocalDateTime.now().minusHours(1));
        voucher.setEndTime(LocalDateTime.now().plusHours(1));
        return voucher;
    }

    @Test
    void createOrder_whenOrderAlreadyExists_returnsFalseWithoutDeductingStock() {
        when(voucherOrdersMapper.selectOne(any())).thenReturn(new VoucherOrders());

        boolean created = service.createOrder(USER_ID, VOUCHER_ID, ORDER_ID);

        assertThat(created).isFalse();
        verify(seckillVouchersMapper, never()).decrementUpdate(any());
        verify(voucherOrdersMapper, never()).insert(any(VoucherOrders.class));
    }

    @Test
    void createOrder_whenVoucherMissing_throwsVoucherNotFound() {
        when(voucherOrdersMapper.selectOne(any())).thenReturn(null);
        when(seckillVouchersMapper.selectById(VOUCHER_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.createOrder(USER_ID, VOUCHER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.VOUCHER_NOT_FOUND.getCode());
    }

    @Test
    void createOrder_whenActivityStillPublishing_throwsIllegalState() {
        SeckillVouchers voucher = publishedVoucher();
        voucher.setStatus(SeckillActivityStatus.PUBLISHING);
        when(voucherOrdersMapper.selectOne(any())).thenReturn(null);
        when(seckillVouchersMapper.selectById(VOUCHER_ID)).thenReturn(voucher);

        assertThatThrownBy(() -> service.createOrder(USER_ID, VOUCHER_ID, ORDER_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createOrder_whenActivityNotPublished_throwsVoucherNotAvailable() {
        SeckillVouchers voucher = publishedVoucher();
        voucher.setStatus(SeckillActivityStatus.OFFLINE);
        when(voucherOrdersMapper.selectOne(any())).thenReturn(null);
        when(seckillVouchersMapper.selectById(VOUCHER_ID)).thenReturn(voucher);

        assertThatThrownBy(() -> service.createOrder(USER_ID, VOUCHER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.VOUCHER_NOT_AVAILABLE.getCode());
    }

    @Test
    void createOrder_whenOutOfTimeWindow_throwsSeckillOutOfTime() {
        SeckillVouchers voucher = publishedVoucher();
        voucher.setBeginTime(LocalDateTime.now().plusHours(1));
        voucher.setEndTime(LocalDateTime.now().plusHours(2));
        when(voucherOrdersMapper.selectOne(any())).thenReturn(null);
        when(seckillVouchersMapper.selectById(VOUCHER_ID)).thenReturn(voucher);

        assertThatThrownBy(() -> service.createOrder(USER_ID, VOUCHER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.SECKILL_OUT_OF_TIME.getCode());
    }

    @Test
    void createOrder_whenStockUpdateAffectsNoRow_throwsVoucherSoldOut() {
        when(voucherOrdersMapper.selectOne(any())).thenReturn(null);
        when(seckillVouchersMapper.selectById(VOUCHER_ID)).thenReturn(publishedVoucher());
        when(seckillVouchersMapper.decrementUpdate(VOUCHER_ID)).thenReturn(0);

        assertThatThrownBy(() -> service.createOrder(USER_ID, VOUCHER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.VOUCHER_SOLD_OUT.getCode());
        verify(voucherOrdersMapper, never()).insert(any(VoucherOrders.class));
    }

    @Test
    void createOrder_whenUniqueIndexViolated_throwsDuplicateOrderMessageException() {
        when(voucherOrdersMapper.selectOne(any())).thenReturn(null);
        when(seckillVouchersMapper.selectById(VOUCHER_ID)).thenReturn(publishedVoucher());
        when(seckillVouchersMapper.decrementUpdate(VOUCHER_ID)).thenReturn(1);
        when(voucherOrdersMapper.insert(any(VoucherOrders.class)))
                .thenThrow(new DuplicateKeyException("uk_user_voucher"));

        assertThatThrownBy(() -> service.createOrder(USER_ID, VOUCHER_ID, ORDER_ID))
                .isInstanceOf(DuplicateOrderMessageException.class);
    }

    @Test
    void createOrder_whenInsertAffectsNoRow_throwsOrderCreateFailed() {
        when(voucherOrdersMapper.selectOne(any())).thenReturn(null);
        when(seckillVouchersMapper.selectById(VOUCHER_ID)).thenReturn(publishedVoucher());
        when(seckillVouchersMapper.decrementUpdate(VOUCHER_ID)).thenReturn(1);
        when(voucherOrdersMapper.insert(any(VoucherOrders.class))).thenReturn(0);

        assertThatThrownBy(() -> service.createOrder(USER_ID, VOUCHER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(ErrorCode.ORDER_CREATE_FAILED.getCode());
    }

    @Test
    void createOrder_whenHappyPath_deductsStockAndInsertsOrder() {
        when(voucherOrdersMapper.selectOne(any())).thenReturn(null);
        when(seckillVouchersMapper.selectById(VOUCHER_ID)).thenReturn(publishedVoucher());
        when(seckillVouchersMapper.decrementUpdate(VOUCHER_ID)).thenReturn(1);
        when(voucherOrdersMapper.insert(any(VoucherOrders.class))).thenReturn(1);

        assertThat(service.createOrder(USER_ID, VOUCHER_ID, ORDER_ID)).isTrue();

        verify(seckillVouchersMapper).decrementUpdate(VOUCHER_ID);
        verify(voucherOrdersMapper).insert(any(VoucherOrders.class));
    }
}
