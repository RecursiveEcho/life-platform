package com.backend.lifeplatform.shop.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 店铺管理展示对象（含审核状态、归属等管理字段）。 */
@Data
public class ShopManageVO {
    /** 主键，店铺 ID */
    private Long id;

    /** 店铺名称 */
    private String name;

    /** 店铺类型 id，关联类型表 */
    private Long typeId;

    /** 所属商家用户 ID；平台管理员创建的店铺可以为空。 */
    private Long ownerId;

    /** 审核状态：PENDING、APPROVED、REJECTED。 */
    private String auditStatus;

    /** 营业状态：OPEN、CLOSED。 */
    private String businessStatus;

    /** 逻辑删除标记。 */
    private int deleted;

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
