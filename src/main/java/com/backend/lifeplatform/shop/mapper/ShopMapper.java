package com.backend.lifeplatform.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.backend.lifeplatform.shop.entity.Shop;
import org.apache.ibatis.annotations.Mapper;

/** 店铺数据访问层。 */
@Mapper
public interface ShopMapper extends BaseMapper<Shop> {
}
