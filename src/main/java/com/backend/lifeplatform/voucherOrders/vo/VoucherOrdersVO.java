package com.backend.lifeplatform.voucherOrders.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 优惠券订单展示对象。 */
@Data
public class VoucherOrdersVO {
    /** 订单 ID */
    private Long id;
    /** 用户抢到的优惠券 ID */
    private Long voucherId;
    /** 订单状态（1 表示已领取） */
    private Integer orderStatus;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 支付时间 */
    private LocalDateTime payTime;
}
