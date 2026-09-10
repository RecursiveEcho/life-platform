package com.backend.lifeplatform.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商家入驻申请实体类
 * 对应数据库表 merchant_application
 */
@Data
@TableName("merchant_application")
public class MerchantApplication {

    /** 主键 ID，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
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

    /** 审核人（处理该申请的审核员） */
    private Long reviewedBy;

    /** 审核时间 */
    private LocalDateTime reviewedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
