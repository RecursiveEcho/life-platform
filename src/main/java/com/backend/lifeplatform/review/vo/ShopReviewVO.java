package com.backend.lifeplatform.review.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 店铺评价展示对象。
 */
@Data
public class ShopReviewVO {

    private Long id;

    private Long userId;

    private Long shopId;

    private Long orderId;

    private Integer rating;

    private String content;

    private LocalDateTime createTime;
}
