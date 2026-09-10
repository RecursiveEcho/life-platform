package com.backend.lifeplatform.shop.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/** 店铺展示对象（对外只读接口使用）。 */
@Data
public class ShopVO {

    /** 主键，店铺 ID */
    private Long id;

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

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
