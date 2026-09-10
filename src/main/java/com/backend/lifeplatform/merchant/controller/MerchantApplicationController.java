package com.backend.lifeplatform.merchant.controller;

import com.backend.lifeplatform.common.result.Result;
import com.backend.lifeplatform.merchant.dto.MerchantApplyDTO;
import com.backend.lifeplatform.merchant.service.MerchantApplicationService;
import com.backend.lifeplatform.merchant.vo.MerchantApplicationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商家入驻申请接口。
 *
 * <p>普通用户提交申请，管理员审核（通过/驳回）并查询申请列表。</p>
 */
@RestController
@RequestMapping("/api/merchant-applications")
@RequiredArgsConstructor
public class MerchantApplicationController {

    private final MerchantApplicationService merchantApplicationService;

    /** 提交商家入驻申请。 */
    @PostMapping
    public Result<Void> apply(@RequestBody @Valid MerchantApplyDTO merchantApplyDTO){
        merchantApplicationService.apply(merchantApplyDTO);
        return Result.success();
    }

    /** 管理员审核通过某条申请，将该用户角色提升为商家。 */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id){
        merchantApplicationService.approve(id);
        return Result.success();
    }

    /** 管理员驳回某条申请，需填写驳回原因。 */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id,@RequestParam String reason){
        merchantApplicationService.reject(id,reason);
        return Result.success();
    }

    /** 分页查询申请列表，可按状态（PENDING/APPROVED/REJECTED）筛选。 */
    @GetMapping
    public Result<List<MerchantApplicationVO>> list(@RequestParam(required = false) String status){
        return Result.success(merchantApplicationService.list(status));
    }
}
