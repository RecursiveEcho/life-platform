package com.backend.lifeplatform.voucherOrders.service;


import com.backend.lifeplatform.common.result.PageResult;
import com.backend.lifeplatform.voucherOrders.vo.VoucherOrdersVO;

/**
 * 优惠券订单业务接口。
 */
public interface VoucherOrdersService {

    /** 提交异步秒杀请求，返回预先生成的订单 ID。 */
    Long submitSeckill(Long voucherId);

    /** 分页查询当前用户的订单，可按状态筛选。 */
    PageResult<VoucherOrdersVO> getOrders(Integer orderStatus, Integer current, Integer size);
}
