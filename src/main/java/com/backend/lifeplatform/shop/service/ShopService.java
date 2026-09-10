package com.backend.lifeplatform.shop.service;

import com.backend.lifeplatform.common.result.PageResult;
import com.backend.lifeplatform.shop.dto.ShopCreateDTO;
import com.backend.lifeplatform.shop.dto.ShopUpdateDTO;
import com.backend.lifeplatform.shop.vo.ShopVO;

/**
 * 店铺业务接口。
 */
public interface ShopService {

    /**
     * 查询店铺列表，可按分类筛选。
     */
    PageResult<ShopVO> list(Long typeId, String keyword,Long current,Long size);

    /**
     * 查询店铺详情。
     */
    ShopVO getById(Long id);

    /**
     * 创建店铺。
     */
    Long createShop(ShopCreateDTO shopDTO);

    /**
     * 部分更新店铺信息。
     */
    void updateShop(Long id, ShopUpdateDTO shopDTO);
}
