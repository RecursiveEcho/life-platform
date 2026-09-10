package com.backend.lifeplatform.voucherOrders.controller;

import com.backend.lifeplatform.common.result.PageResult;
import com.backend.lifeplatform.common.result.Result;
import com.backend.lifeplatform.voucherOrders.service.VoucherOrdersService;
import com.backend.lifeplatform.voucherOrders.vo.VoucherOrdersVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 优惠券订单接口。
 */
@RestController
@RequestMapping("/api/voucher_orders")
@RequiredArgsConstructor
public class VoucherOrdersController {

    private final VoucherOrdersService voucherOrdersService;

    /** 抢购秒杀券，成功返回新建订单 ID。 */
    @PostMapping("/seckill/{voucherId}")
    public Result<Long> seckillVoucher(@PathVariable Long voucherId){
        return Result.success(voucherOrdersService.submitSeckill(voucherId));
    }

    /** 分页查询当前登录用户的订单，可按订单状态筛选。 */
    @GetMapping("/my")
    public Result<PageResult<VoucherOrdersVO>> my
            (@RequestParam(required = false) Integer orderStatus,
             @RequestParam(defaultValue = "1") Integer current,
             @RequestParam(defaultValue = "10") Integer size){
        return Result.success(voucherOrdersService.getOrders(orderStatus,current,size));
    }
}
