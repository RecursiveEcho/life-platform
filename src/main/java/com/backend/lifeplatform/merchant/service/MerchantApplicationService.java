package com.backend.lifeplatform.merchant.service;

import com.backend.lifeplatform.merchant.dto.MerchantApplyDTO;
import com.backend.lifeplatform.merchant.vo.MerchantApplicationVO;

import java.util.List;


/**
 * 商家入驻申请业务接口。
 */
public interface MerchantApplicationService {

    /** 提交商家入驻申请。 */
    void apply(MerchantApplyDTO dto);

    /** 审核通过申请，将用户角色提升为商家。 */
    void approve(Long applicationId);

    /** 驳回申请并记录驳回原因。 */
    void reject(Long applicationId,String reason);

    /** 查询申请列表，可按状态筛选。 */
    List<MerchantApplicationVO> list(String status);

    /** 查询申请详情。 */
    MerchantApplicationVO detail (Long applicationId);
}
