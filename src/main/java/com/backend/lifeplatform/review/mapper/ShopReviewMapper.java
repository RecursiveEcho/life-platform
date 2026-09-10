package com.backend.lifeplatform.review.mapper;

import com.backend.lifeplatform.review.entity.ShopReview;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 店铺评价数据访问层。
 */
@Mapper
public interface ShopReviewMapper extends BaseMapper<ShopReview> {
}
