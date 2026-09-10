package com.backend.lifeplatform.voucher.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建秒杀场次的请求参数。
 *
 * <p>秒杀场次复用普通券的 id 作为主键，因此创建前需要先存在对应的普通券；
 * Service 层还会继续校验店铺归属、时间顺序和库存上限。</p>
 */
@Data
public class SeckillVoucherCreateDTO {

    /** 要创建秒杀场次的普通券 id。 */
    @NotNull(message = "优惠券ID不能为空")
    private Long voucherId;

    /** 本场秒杀可抢库存，至少为 1。 */
    @NotNull(message = "秒杀库存不能为空")
    @Min(value = 1, message = "秒杀库存必须大于0")
    private Integer stock;

    /** 秒杀开始时间，必须早于结束时间。 */
    @NotNull(message = "开始时间不能为空")
    private LocalDateTime beginTime;

    /** 秒杀结束时间，结束后数据库事务层也会拒绝下单。 */
    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;
}
