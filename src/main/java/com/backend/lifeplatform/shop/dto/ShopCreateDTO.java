package com.backend.lifeplatform.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

/** 店铺创建请求参数（创建时必填的字段）。 */
@Data
public class ShopCreateDTO {

    /** 店铺名称 */
    @NotBlank(message = "名字不能为空")
    private String name;

    /** 店铺类型 id，关联类型表 */
    @NotNull(message = "店铺类型不能为空")
    private Long typeId;

    /** 店铺地址 */
    @NotBlank(message = "店铺地址不能为空")
    private String address;

    /** 经度 */
    @NotNull(message = "经度不能为空")
    private BigDecimal longitude;

    /** 纬度 */
    @NotNull(message = "纬度不能为空")
    private BigDecimal latitude;

    /** 人均消费 */
    @DecimalMin(value = "0.00", message = "人均消费不能为负数")
    private BigDecimal avgPrice;
}
