package com.backend.lifeplatform.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建店铺评价请求。
 */
@Data
public class ReviewCreateDTO {

    @NotNull(message = "订单 ID 不能为空")
    private Long orderId;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分最低为 1 分")
    @Max(value = 5, message = "评分最高为 5 分")
    private Integer rating;

    @NotBlank(message = "评价内容不能为空")
    @Size(max = 1000, message = "评价内容不能超过 1000 个字符")
    private String content;
}
