package com.backend.lifeplatform.voucher.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券详情展示对象。
 * <p>字段分两类：普通券信息（voucher* 前缀）与秒杀场次信息（seckill* 前缀），
 * 前端秒杀页可同时拿到两套时间与库存做倒计时、限购与售罄判断。</p>
 */
@Data
public class VouchersVO {
    /** 优惠券 ID */
    private Long id;

    /** 所属店铺 ID */
    private Long shopId;

    /** 标题 */
    private String title;

    /** 副标题 */
    private String subTitle;

    /** 优惠金额（面值） */
    private BigDecimal discountAmount;

    /** 售价 */
    private BigDecimal payValue;

    /** 普通券库存 */
    private Integer voucherStock;

    /** 普通券可售开始时间 */
    private LocalDateTime vouchersBeginTime;

    /** 普通券可售结束时间 */
    private LocalDateTime vouchersEndTime;

    /** 秒杀场次库存 */
    private Integer seckillStock;

    /** 秒杀开始时间 */
    private LocalDateTime seckillBeginTime;

    /** 秒杀结束时间 */
    private LocalDateTime seckillEndTime;
}
