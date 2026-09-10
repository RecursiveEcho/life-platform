package com.backend.lifeplatform.voucher.mapper;

import com.backend.lifeplatform.voucher.entity.SeckillVouchers;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 秒杀场次数据访问层。 */
@Mapper
public interface SeckillVouchersMapper extends BaseMapper<SeckillVouchers> {

    /**
     * 原子扣减秒杀库存：仅当当前库存 &gt; 0 时才减一，返回值 = 受影响行数。
     * 用 SQL 层的条件保证并发下不会超卖，返回 0 表示抢光了。
     */
    @Update("""
        update seckill_vouchers
        set stock = stock - 1
        where voucher_id = #{voucherId}
        AND stock > 0;
""")
    int decrementUpdate(@Param("voucherId") Long voucherId);

    /** 把草稿状态的秒杀活动置为「发布中」，仅 DRAFT→PUBLISHING 时生效，返回受影响行数。 */
    @Update("""
            update life_platform.seckill_vouchers
            set seckill_vouchers.status = 'PUBLISHING'
            where  seckill_vouchers.voucher_id = #{voucherId}
            and seckill_vouchers.status ='DRAFT'
            """)
    int markPublishing(@Param("voucherId") Long voucherId);

    /** 把发布中的活动置为「已发布」，仅 PUBLISHING→PUBLISHED 时生效，返回受影响行数。 */
    @Update("""
             update life_platform.seckill_vouchers
             set seckill_vouchers.status = 'PUBLISHED'
             where seckill_vouchers.voucher_id = #{voucherId}
             and seckill_vouchers.status= 'PUBLISHING'
             """)
    int markPublished(@Param("voucherId") Long voucherId);
}
