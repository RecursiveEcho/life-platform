package com.backend.lifeplatform.voucher.service;


import com.backend.lifeplatform.common.result.PageResult;
import com.backend.lifeplatform.voucher.dto.VoucherCreateDTO;
import com.backend.lifeplatform.voucher.dto.VoucherUpdateDTO;
import com.backend.lifeplatform.voucher.dto.SeckillVoucherCreateDTO;
import com.backend.lifeplatform.voucher.vo.VouchersVO;

/**
 * 优惠券业务接口。
 */
public interface VoucherService {

    /** 创建普通优惠券。 */
    Long createVoucher(VoucherCreateDTO request);

    /** PATCH 更新普通优惠券。 */
    void updateVoucher(Long voucherId, VoucherUpdateDTO request);

    /**
     * 分页查询店铺当前可见的秒杀优惠券。
     */
    PageResult<VouchersVO> listByShop(
            Long shopId,
            String keyword,
            Long current,
            Long size
    );

    /**
     * 查询优惠券详情（含秒杀场次信息）。
     */
    VouchersVO getDetails(Long id);

    /** 为已有普通券创建一条秒杀场次记录，初始状态为 DRAFT。 */
    void createSeckillVoucher(SeckillVoucherCreateDTO dto);

    /** 发布秒杀场次：状态置为「发布中」并写发件箱事件，由定时任务完成 Redis 初始化与上线。 */
    void publishSeckillVoucher(Long voucherId);
}
