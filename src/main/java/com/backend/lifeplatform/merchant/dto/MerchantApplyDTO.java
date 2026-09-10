package com.backend.lifeplatform.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 商家入驻申请请求参数。 */
@Data
public class MerchantApplyDTO {

    @NotBlank(message = "商家名字不能为空")
    private String businessName;

    @NotBlank(message = "联系人名字不能为空")
    private String contactName;

    @NotBlank(message = "联系人手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$",message = "联系人手机号格式不正确")
    private String contactPhone;
}
