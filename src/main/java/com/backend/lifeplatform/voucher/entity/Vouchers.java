package com.backend.lifeplatform.voucher.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 优惠券主信息实体，对应表 vouchers。
 * 一条优惠券是某一店铺可售卖的券种；若另设了秒杀活动，则对应一条 seckill_vouchers 记录。
 */
@Data
@TableName("vouchers")
public class Vouchers {

    /** 主键 ID，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属店铺 ID */
    @NotNull(message = "商户ID不能为空")
    private Long shopId;

    /** 优惠券标题 */
    @NotNull(message = "标题不能为空")
    private String title;

    /** 副标题（可选） */
    private String subTitle;

    /** 优惠金额（面值），最多两位小数 */
    @NotNull(message = "优惠金额不能为空")
    @Digits(integer = 8, fraction = 2, message = "优惠金额必须是有效的数字，最多两位小数")
    private BigDecimal discountAmount;

    /** 售价（用户购买需支付的金额），最多两位小数 */
    @NotNull(message = "售价不能为空")
    @Digits(integer = 8, fraction = 2, message = "售价必须是有效的数字，最多两位小数")
    private BigDecimal payValue;

    /** 库存数量 */
    @NotNull(message = "库存不能为空")
    private Integer stock;

    /** 可售开始时间 */
    private LocalDateTime beginTime;

    /** 可售结束时间 */
    private LocalDateTime endTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
