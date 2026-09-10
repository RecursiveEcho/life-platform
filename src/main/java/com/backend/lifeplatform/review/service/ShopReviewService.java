package com.backend.lifeplatform.review.service;

import com.backend.lifeplatform.common.result.PageResult;
import com.backend.lifeplatform.review.dto.ReviewCreateDTO;
import com.backend.lifeplatform.review.vo.ShopReviewVO;

/**
 * 店铺评价业务接口。
 */
public interface ShopReviewService {

    /**
     * 当前用户基于自己的订单创建评价。
     */
    Long create(ReviewCreateDTO request);

    /**
     * 分页查询店铺的公开评价。
     */
    PageResult<ShopReviewVO> listByShop(Long shopId, Long current, Long size);
}
