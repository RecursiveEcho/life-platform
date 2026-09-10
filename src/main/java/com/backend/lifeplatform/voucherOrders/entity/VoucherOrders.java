package com.backend.lifeplatform.voucherOrders.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 优惠券订单实体，对应表 voucher_orders。
 * 用户抢券成功后生成一条订单，作为后续核销 / 支付流程的依据。
 */
@Data
@TableName("voucher_orders")
public class VoucherOrders {

    /** 主键 ID，由应用侧 IdWorker 预先生成（IdType.INPUT 表示不自增），用于消息幂等追踪 */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /** 下单用户 ID */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /** 被抢购的优惠券 ID */
    @NotNull(message = "代金劵ID不能为空")
    private Long voucherId;

    /** 订单状态（当前秒杀成功固定置为 1，表示已领取） */
    @NotNull(message = "订单状态不能为空")
    private Integer orderStatus;

    /** 创建（下单）时间 */
    private LocalDateTime createTime;

    /** 支付时间（预留字段，支付流程尚未接入） */
    private LocalDateTime payTime;
}
