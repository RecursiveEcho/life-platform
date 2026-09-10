package com.backend.lifeplatform.shop.controller;

import com.backend.lifeplatform.common.result.PageResult;
import com.backend.lifeplatform.common.result.Result;

import com.backend.lifeplatform.shop.dto.ShopCreateDTO;
import com.backend.lifeplatform.shop.dto.ShopUpdateDTO;
import com.backend.lifeplatform.shop.service.ShopService;
import com.backend.lifeplatform.shop.vo.ShopVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;


/**
 * 店铺对外接口：游客可浏览已上架店铺，商家 / 管理员负责店铺的新建与维护。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shops")
@Tag(name = "店铺管理", description = "店铺查询与管理接口")
public class ShopController {

    private final ShopService shopService;


    @Operation(summary = "查询店铺列表")
    @GetMapping
    public Result<PageResult<ShopVO>> list(@RequestParam(required = false) Long typeId,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "1") Long current,
                                           @RequestParam(defaultValue = "10") Long size

    ) {
        return Result.success(shopService.list(typeId,keyword,current,size));
    }

    @Operation(summary = "查询店铺详情")
    @GetMapping("/{id}")
    public Result<ShopVO> getById(
            @Parameter(description = "店铺 ID", required = true)
            @PathVariable Long id) {
        return Result.success(shopService.getById(id));
    }

    @Operation(summary = "创建店铺")
    @PostMapping
    public Result<Long> create(@RequestBody @Valid ShopCreateDTO shopCreateDTO) {
        return Result.success(shopService.createShop(shopCreateDTO));
    }

    @Operation(summary = "修改店铺")
    @PatchMapping("/{id}")
    public Result<Void> update(
                               @Parameter(description = "店铺 ID", required = true)
                               @PathVariable Long id,
                               @RequestBody @Valid ShopUpdateDTO shopUpdateDTO) {
        shopService.updateShop(id, shopUpdateDTO);
        return Result.success();
    }
}
