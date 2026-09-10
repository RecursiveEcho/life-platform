package com.backend.lifeplatform.voucher.controller;

import com.backend.lifeplatform.common.result.PageResult;
import com.backend.lifeplatform.common.result.Result;
import com.backend.lifeplatform.voucher.dto.VoucherCreateDTO;
import com.backend.lifeplatform.voucher.dto.VoucherUpdateDTO;
import com.backend.lifeplatform.voucher.dto.SeckillVoucherCreateDTO;
import com.backend.lifeplatform.voucher.service.VoucherService;
import com.backend.lifeplatform.voucher.vo.VouchersVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 优惠券接口：详情查询、秒杀场次创建与活动发布。
 */
@RestController
@RequestMapping("/api/vouchers")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    /** 管理员或店铺所属商家创建普通优惠券。 */
    @PostMapping
    public Result<Long> create(
            @Valid @RequestBody VoucherCreateDTO request) {
        return Result.success(voucherService.createVoucher(request));
    }

    /** 管理员或店铺所属商家修改普通优惠券。 */
    @PatchMapping("/{id}")
    public Result<Void> update(
            @PathVariable Long id,
            @Valid @RequestBody VoucherUpdateDTO request) {
        voucherService.updateVoucher(id, request);
        return Result.success();
    }

    /** 查询店铺当前可见的秒杀优惠券，支持标题关键词和分页。 */
    @GetMapping("/shop/{shopId}")
    public Result<PageResult<VouchersVO>> listByShop(
            @PathVariable Long shopId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size) {
        return Result.success(
                voucherService.listByShop(shopId, keyword, current, size)
        );
    }

    /** 查询优惠券详情，返回内容同时含普通券信息与对应秒杀场次信息。 */
    @GetMapping("/{id}")
    public Result<VouchersVO> getDetail(@PathVariable Long id){
        return Result.success(voucherService.getDetails(id));
    }


    /** 管理员或店铺所属商家为已有优惠券创建秒杀场次。 */
    @PostMapping("/create/seckillVoucher")
    public Result<Void> createSeckillVoucher(@Valid @RequestBody
                                                 SeckillVoucherCreateDTO seckillVoucherCreateDTO){
        voucherService.createSeckillVoucher(seckillVoucherCreateDTO);
        return Result.success();
    }

    /** 管理员或店铺所属商家发布秒杀活动。 */
    @PutMapping("/published/{id}")
    public Result<Void> publishSeckillVoucher(@PathVariable(value = "id") Long voucherId){
        voucherService.publishSeckillVoucher(voucherId);
        return Result.success();
    }
}
