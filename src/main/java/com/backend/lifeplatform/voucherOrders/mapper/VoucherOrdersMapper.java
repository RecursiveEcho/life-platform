package com.backend.lifeplatform.voucherOrders.mapper;

import com.backend.lifeplatform.voucherOrders.entity.VoucherOrders;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 优惠券订单数据访问层。 */
@Mapper
public interface VoucherOrdersMapper extends BaseMapper<VoucherOrders> {
}
