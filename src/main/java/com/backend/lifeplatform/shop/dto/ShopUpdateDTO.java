package com.backend.lifeplatform.shop.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 店铺部分更新参数。
 *
 * <p>PATCH 的字段全部允许不传，具体的非空字段由 Service 复制到实体。</p>
 */
@Data
public class ShopUpdateDTO {
    /** 店铺名称 */
    private String name;

    /** 店铺类型 id，关联类型表 */
    private Long typeId;

    /** 店铺地址 */
    private String address;

    /** 经度 */
    private BigDecimal longitude;

    /** 纬度 */
    private BigDecimal latitude;

    /** 人均消费 */
    private BigDecimal avgPrice;
}
