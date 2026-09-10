package com.backend.lifeplatform.voucher.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 商家创建普通优惠券请求。 */
@Data
public class VoucherCreateDTO {

    @NotNull(message = "店铺 ID 不能为空")
    private Long shopId;

    @NotBlank(message = "优惠券标题不能为空")
    @Size(max = 100, message = "优惠券标题不能超过 100 个字符")
    private String title;

    @Size(max = 255, message = "优惠券副标题不能超过 255 个字符")
    private String subTitle;

    @NotNull(message = "优惠金额不能为空")
    @DecimalMin(value = "0.00", message = "优惠金额不能为负数")
    @Digits(integer = 8, fraction = 2, message = "优惠金额最多 8 位整数和 2 位小数")
    private BigDecimal discountAmount;

    @NotNull(message = "售价不能为空")
    @DecimalMin(value = "0.00", message = "售价不能为负数")
    @Digits(integer = 8, fraction = 2, message = "售价最多 8 位整数和 2 位小数")
    private BigDecimal payValue;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负数")
    private Integer stock;

    private LocalDateTime beginTime;

    private LocalDateTime endTime;
}
