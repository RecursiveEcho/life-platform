package com.backend.lifeplatform.review.controller;

import com.backend.lifeplatform.common.result.PageResult;
import com.backend.lifeplatform.common.result.Result;
import com.backend.lifeplatform.review.dto.ReviewCreateDTO;
import com.backend.lifeplatform.review.service.ShopReviewService;
import com.backend.lifeplatform.review.vo.ShopReviewVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 店铺评价接口。
 */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ShopReviewController {

    private final ShopReviewService shopReviewService;

    /**
     * 当前登录用户根据自己的订单提交评价。
     */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ReviewCreateDTO request) {
        return Result.success(shopReviewService.create(request));
    }

    /**
     * 游客也可以查看店铺公开评价。
     */
    @GetMapping("/shop/{shopId}")
    public Result<PageResult<ShopReviewVO>> listByShop(
            @PathVariable Long shopId,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size) {
        return Result.success(shopReviewService.listByShop(shopId, current, size));
    }
}
