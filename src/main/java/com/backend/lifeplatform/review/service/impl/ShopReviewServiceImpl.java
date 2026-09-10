package com.backend.lifeplatform.review.service.impl;

import com.backend.lifeplatform.common.enums.ErrorCode;
import com.backend.lifeplatform.common.exception.BusinessException;
import com.backend.lifeplatform.common.result.PageResult;
import com.backend.lifeplatform.common.utils.SecurityUserContext;
import com.backend.lifeplatform.review.dto.ReviewCreateDTO;
import com.backend.lifeplatform.review.entity.ShopReview;
import com.backend.lifeplatform.review.mapper.ShopReviewMapper;
import com.backend.lifeplatform.review.service.ShopReviewService;
import com.backend.lifeplatform.review.vo.ShopReviewVO;
import com.backend.lifeplatform.voucher.entity.Vouchers;
import com.backend.lifeplatform.voucher.mapper.VouchersMapper;
import com.backend.lifeplatform.voucherOrders.entity.VoucherOrders;
import com.backend.lifeplatform.voucherOrders.mapper.VoucherOrdersMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 店铺评价业务实现。
 *
 * <p>V1 只保留一个核心规则：评价资格来自订单，而不是由前端直接提交 shopId。
 * 服务端根据订单反查优惠券和店铺，避免用户评价与实际消费店铺不一致。</p>
 */
@Service
@RequiredArgsConstructor
public class ShopReviewServiceImpl implements ShopReviewService {

    private static final int NORMAL_STATUS = 1;

    private final ShopReviewMapper shopReviewMapper;
    private final VoucherOrdersMapper voucherOrdersMapper;
    private final VouchersMapper vouchersMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ReviewCreateDTO request) {
        Long userId = SecurityUserContext.requireUserId();

        VoucherOrders order = voucherOrdersMapper.selectById(request.getOrderId());
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
        }

        Vouchers voucher = vouchersMapper.selectById(order.getVoucherId());
        if (voucher == null) {
            throw new BusinessException(ErrorCode.VOUCHER_NOT_FOUND, "优惠券不存在");
        }

        long existingCount = shopReviewMapper.selectCount(
                new LambdaQueryWrapper<ShopReview>()
                        .eq(ShopReview::getOrderId, order.getId())
                        .eq(ShopReview::getUserId, userId)
        );
        if (existingCount > 0) {
            throw new BusinessException(
                    ErrorCode.REVIEW_ALREADY_SUBMITTED,
                    "该订单已经评价过"
            );
        }

        ShopReview review = new ShopReview();
        review.setUserId(userId);
        review.setShopId(voucher.getShopId());
        review.setOrderId(order.getId());
        review.setRating(request.getRating());
        review.setContent(request.getContent().trim());
        review.setStatus(NORMAL_STATUS);

        try {
            if (shopReviewMapper.insert(review) != 1) {
                throw new BusinessException(ErrorCode.DB_ERROR, "评价创建失败");
            }
        } catch (DuplicateKeyException e) {
            // 并发重复提交由数据库唯一索引兜底，统一转换为业务错误。
            throw new BusinessException(
                    ErrorCode.REVIEW_ALREADY_SUBMITTED,
                    "该订单已经评价过"
            );
        }
        return review.getId();
    }

    @Override
    public PageResult<ShopReviewVO> listByShop(Long shopId, Long current, Long size) {
        if (shopId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "店铺 ID 不能为空");
        }

        IPage<ShopReview> page = shopReviewMapper.selectPage(
                new Page<>(current, size),
                new LambdaQueryWrapper<ShopReview>()
                        .eq(ShopReview::getShopId, shopId)
                        .eq(ShopReview::getStatus, NORMAL_STATUS)
                        .orderByDesc(ShopReview::getCreateTime)
                        .orderByDesc(ShopReview::getId)
        );

        List<ShopReviewVO> records = page.getRecords().stream()
                .map(this::toVO)
                .toList();
        return PageResult.of(
                records,
                page.getTotal(),
                page.getCurrent(),
                page.getSize()
        );
    }

    private ShopReviewVO toVO(ShopReview review) {
        ShopReviewVO vo = new ShopReviewVO();
        BeanUtils.copyProperties(review, vo);
        return vo;
    }
}
