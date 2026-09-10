package com.backend.lifeplatform.voucher.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀场次实体，对应表 seckill_vouchers。
 * 以普通券 id 作为主键（voucher_id），即一条普通券最多对应一个秒杀场次。
 */
@Data
@TableName("seckill_vouchers")
public class SeckillVouchers {

    /** 对应普通券的主键 ID；IdType.INPUT 表示由插入方显式赋值，不自增 */
    @TableId(value = "voucher_id", type = IdType.INPUT)
    private Long voucherId;

    /** 秒杀场次库存 */
    private Integer stock;

    /** 秒杀开始时间 */
    private LocalDateTime beginTime;

    /** 秒杀结束时间 */
    private LocalDateTime endTime;

    /** 活动状态：DRAFT、PUBLISHING、PUBLISHED、OFFLINE，见 SeckillActivityStatus */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
