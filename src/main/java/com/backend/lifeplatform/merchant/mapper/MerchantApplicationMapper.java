package com.backend.lifeplatform.merchant.mapper;

import com.backend.lifeplatform.merchant.entity.MerchantApplication;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 商家入驻申请数据访问层。 */
@Mapper
public interface MerchantApplicationMapper extends BaseMapper<MerchantApplication> {
}
