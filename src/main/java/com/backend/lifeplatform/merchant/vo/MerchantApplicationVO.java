package com.backend.lifeplatform.merchant.vo;


import lombok.Data;

import java.time.LocalDateTime;

/** 商家入驻申请展示对象。 */
@Data
public class MerchantApplicationVO {
    /** 申请主键 ID */
    private Long id;

    /** 提交申请的用户 ID */
    private Long userId;

    /** 商家名称 */
    private String businessName;

    /** 联系人姓名 */
    private String contactName;

    /** 联系人电话 */
    private String contactPhone;

    /** 申请状态（如：待审核 / 已通过 / 已驳回） */
    private String status;

    /** 驳回原因（审核未通过时填写） */
    private String rejectReason;

    /** 审核时间 */
    private LocalDateTime reviewedAt;

    /** 创建时间 */
    private LocalDateTime createTime;
}
