package com.backend.lifeplatform.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 店铺评价实体。
 *
 * <p>一条评价同时关联用户、店铺和订单。
 * 订单关联用于证明评价资格，店铺关联用于高效查询店铺评价。</p>
 */
@Data
@TableName("shop_reviews")
public class ShopReview {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long shopId;

    private Long orderId;

    private Integer rating;

    private String content;

    /** 1=正常，0=隐藏。V1 只写入正常评价，后续可接审核/屏蔽。 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
