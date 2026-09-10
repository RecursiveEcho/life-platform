package com.backend.lifeplatform.voucher.mapper;

import com.backend.lifeplatform.voucher.entity.Vouchers;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.backend.lifeplatform.voucher.vo.VouchersVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Mapper;

/** 优惠券主信息数据访问层。 */
@Mapper
public interface VouchersMapper extends BaseMapper<Vouchers> {

    /**
     * 只查询已经发布且尚未结束的秒杀券。
     *
     * <p>把筛选和分页交给数据库完成，避免先查全部优惠券再在 Java 内存中过滤。</p>
     */
    @Select("""
            SELECT v.id,
                   v.shop_id,
                   v.title,
                   v.sub_title,
                   v.discount_amount,
                   v.pay_value,
                   v.stock AS voucher_stock,
                   v.begin_time AS vouchers_begin_time,
                   v.end_time AS vouchers_end_time,
                   sv.stock AS seckill_stock,
                   sv.begin_time AS seckill_begin_time,
                   sv.end_time AS seckill_end_time
            FROM vouchers v
            INNER JOIN seckill_vouchers sv
                    ON sv.voucher_id = v.id
            WHERE v.shop_id = #{shopId}
              AND sv.status = 'PUBLISHED'
              AND sv.end_time >= NOW()
              AND (
                    #{keyword} IS NULL
                    OR #{keyword} = ''
                    OR v.title LIKE CONCAT('%', #{keyword}, '%')
                  )
            ORDER BY v.id DESC
            """)
    IPage<VouchersVO> selectPublishedPage(
            Page<VouchersVO> page,
            @Param("shopId") Long shopId,
            @Param("keyword") String keyword
    );
}
