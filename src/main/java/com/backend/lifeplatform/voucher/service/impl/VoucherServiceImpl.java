package com.backend.lifeplatform.voucher.service.impl;

import com.backend.lifeplatform.common.enums.ErrorCode;
import com.backend.lifeplatform.common.exception.BusinessException;
import com.backend.lifeplatform.common.result.PageResult;
import com.backend.lifeplatform.common.utils.RedisJsonCacheTool;
import com.backend.lifeplatform.common.utils.SecurityUserContext;
import com.backend.lifeplatform.shop.entity.Shop;
import com.backend.lifeplatform.shop.mapper.ShopMapper;
import com.backend.lifeplatform.user.entity.User;
import com.backend.lifeplatform.user.mapper.UserMapper;
import com.backend.lifeplatform.voucher.dto.SeckillVoucherCreateDTO;
import com.backend.lifeplatform.voucher.dto.VoucherCreateDTO;
import com.backend.lifeplatform.voucher.dto.VoucherUpdateDTO;
import com.backend.lifeplatform.voucher.constant.SeckillActivityStatus;
import com.backend.lifeplatform.voucher.entity.SeckillVouchers;
import com.backend.lifeplatform.voucher.entity.Vouchers;
import com.backend.lifeplatform.voucher.mapper.SeckillVouchersMapper;
import com.backend.lifeplatform.voucher.mapper.VouchersMapper;
import com.backend.lifeplatform.voucher.service.SeckillPublishCommandService;
import com.backend.lifeplatform.voucher.service.VoucherService;
import com.backend.lifeplatform.voucher.vo.VouchersVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 优惠券业务实现，覆盖详情查询、秒杀场次创建与活动发布。
 *
 * <p>详情页需同时展示普通券与秒杀场次信息，两者主键一致（voucherId == 券 id），
 * 结果整体写入 Redis 缓存；发布流程则通过状态流转与发件箱事件保证活动上线可靠。</p>
 */
@Service
@RequiredArgsConstructor
public class VoucherServiceImpl implements VoucherService {

    private final VouchersMapper vouchersMapper;
    private final RedisJsonCacheTool redisJsonCacheTool;
    private final SeckillVouchersMapper seckillVouchersMapper;
    private final ShopMapper shopMapper;
    private final UserMapper userMapper;


    /**
     * 优惠券详情缓存 key 前缀
     */
    private static final String CACHE_VOUCHER_DETAIL = "cache_voucher_detail:";

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String MERCHANT_ROLE = "MERCHANT";
    private final SeckillPublishCommandService seckillPublishCommandService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createVoucher(VoucherCreateDTO request) {
        Long userId = SecurityUserContext.requireUserId();
        Shop shop = requireWritableShop(request.getShopId(), userId);
        validateMoneyAndPeriod(
                request.getDiscountAmount(),
                request.getPayValue(),
                request.getBeginTime(),
                request.getEndTime()
        );

        Vouchers voucher = new Vouchers();
        BeanUtils.copyProperties(request, voucher);
        voucher.setTitle(request.getTitle().trim());
        if (request.getSubTitle() != null) {
            voucher.setSubTitle(request.getSubTitle().trim());
        }
        voucher.setShopId(shop.getId());

        if (vouchersMapper.insert(voucher) != 1 || voucher.getId() == null) {
            throw new BusinessException(ErrorCode.DB_ERROR, "优惠券创建失败");
        }
        return voucher.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateVoucher(Long voucherId, VoucherUpdateDTO request) {
        if (voucherId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "优惠券 ID 不能为空");
        }

        Vouchers voucher = vouchersMapper.selectById(voucherId);
        if (voucher == null) {
            throw new BusinessException(ErrorCode.VOUCHER_NOT_FOUND, "优惠券不存在");
        }

        Long userId = SecurityUserContext.requireUserId();
        requireWritableShop(voucher.getShopId(), userId);
        ensurePatchHasField(request);

        SeckillVouchers seckillVoucher = seckillVouchersMapper.selectById(voucherId);
        if (seckillVoucher != null
                && !SeckillActivityStatus.DRAFT.equals(seckillVoucher.getStatus())) {
            throw new BusinessException(
                    ErrorCode.INVALID_OPERATION,
                    "秒杀活动发布后不能直接修改优惠券"
            );
        }

        applyVoucherPatch(voucher, request);
        if (seckillVoucher != null
                && SeckillActivityStatus.DRAFT.equals(seckillVoucher.getStatus())
                && voucher.getStock() < seckillVoucher.getStock()) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    "普通券库存不能低于秒杀库存"
            );
        }
        validateMoneyAndPeriod(
                voucher.getDiscountAmount(),
                voucher.getPayValue(),
                voucher.getBeginTime(),
                voucher.getEndTime()
        );

        if (vouchersMapper.updateById(voucher) != 1) {
            throw new BusinessException(ErrorCode.DB_ERROR, "优惠券修改失败");
        }

        registerAfterCommit(() -> {
            try {
                redisJsonCacheTool.delete(CACHE_VOUCHER_DETAIL + voucherId);
            } catch (RuntimeException ignored) {
                // 缓存删除失败不回滚已经提交的优惠券修改。
            }
        });
    }

    /**
     * 查询店铺对用户可见的秒杀优惠券。
     *
     * <p>列表只展示 PUBLISHED 且尚未结束的活动；草稿、发布中、已下线和已过期活动
     * 不应该暴露给用户。分页和筛选在数据库完成，避免应用层加载无关数据。</p>
     */
    @Override
    public PageResult<VouchersVO> listByShop(
            Long shopId,
            String keyword,
            Long current,
            Long size
    ) {
        if (shopId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "店铺 ID 不能为空");
        }
        if (current == null || current < 1 || size == null || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "分页参数无效");
        }

        String normalizedKeyword = StringUtils.hasText(keyword)
                ? keyword.trim()
                : null;

        IPage<VouchersVO> page = vouchersMapper.selectPublishedPage(
                new Page<>(current, size),
                shopId,
                normalizedKeyword
        );
        return PageResult.of(
                page.getRecords(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize()
        );
    }


    @Override
    public VouchersVO getDetails(Long id) {
        String redisKey = CACHE_VOUCHER_DETAIL + id;
        return redisJsonCacheTool.getObject(redisKey, VouchersVO.class,
                () -> {
                    // 主查询：按券 id 查普通券信息
                    Vouchers vouchers = vouchersMapper.selectOne(new LambdaQueryWrapper<Vouchers>()
                            .eq(Vouchers::getId, id)
                    );
                    // 场次查询：秒杀场次主键与普通券 id 一致，直接等值匹配
                    SeckillVouchers seckillVouchers = seckillVouchersMapper.selectOne(new LambdaQueryWrapper<SeckillVouchers>()
                            .eq(SeckillVouchers::getVoucherId, id)
                            .eq(SeckillVouchers::getStatus, SeckillActivityStatus.PUBLISHED)
                            .ge(SeckillVouchers::getEndTime, LocalDateTime.now())
                    );

                    if (vouchers == null) {
                        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "优惠券不存在");
                    }

                    if (seckillVouchers == null) {
                        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "此劵不是秒杀卷");
                    }

                    return toVO(vouchers, seckillVouchers);
                });
    }

    /**
     * 手工把两张表的数据映射到展示对象（VO 字段名与实体不对应，故不用 BeanUtils）。
     */
    private VouchersVO toVO(Vouchers vouchers, SeckillVouchers seckillVouchers) {
        VouchersVO vouchersVO = new VouchersVO();
        vouchersVO.setId(vouchers.getId());
        vouchersVO.setVoucherStock(vouchers.getStock());
        vouchersVO.setTitle(vouchers.getTitle());
        if (StringUtils.hasText(vouchers.getSubTitle())) {
            vouchersVO.setSubTitle(vouchers.getSubTitle());
        }
        vouchersVO.setPayValue(vouchers.getPayValue());
        vouchersVO.setShopId(vouchers.getShopId());
        vouchersVO.setDiscountAmount(vouchers.getDiscountAmount());
        vouchersVO.setVouchersBeginTime(vouchers.getBeginTime());
        vouchersVO.setVouchersEndTime(vouchers.getEndTime());
        vouchersVO.setSeckillEndTime(seckillVouchers.getEndTime());
        vouchersVO.setSeckillStock(seckillVouchers.getStock());
        vouchersVO.setSeckillBeginTime(seckillVouchers.getBeginTime());
        return vouchersVO;
    }

    /**
     * 创建秒杀场次：校验券归属与操作权限后，以「草稿」状态落库，等待后续发布。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createSeckillVoucher(SeckillVoucherCreateDTO seckillVoucherCreateDTO) {
        Long userId = SecurityUserContext.requireUserId();
        Vouchers vouchers = vouchersMapper.selectById(seckillVoucherCreateDTO.getVoucherId());
        User user = userMapper.selectById(userId);

        if (vouchers == null) {
            throw new BusinessException(ErrorCode.VOUCHER_NOT_FOUND, "优惠卷不存在");
        }

        if (user == null) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION, "非法操作");
        }

        boolean isAdmin = SecurityUserContext.hasRole(ADMIN_ROLE);
        boolean isMerchant = SecurityUserContext.hasRole(MERCHANT_ROLE);

        if (!isAdmin && !isMerchant) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION, "无权创建秒杀活动");
        }

        Shop shop = shopMapper.selectById(vouchers.getShopId());

        if (shop == null) {
            throw new BusinessException(ErrorCode.SHOP_NOT_FOUND, "店铺不存在");
        }

        if (isMerchant && !Objects.equals(shop.getOwnerId(), userId)) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION, "无权操作该店铺");
        }


        if (seckillVouchersMapper.selectById(seckillVoucherCreateDTO.getVoucherId()) != null) {
            throw new BusinessException(ErrorCode.RESOURCE_EXISTS, "该优惠卷已经被创建成秒杀活动");
        }


        LocalDateTime beginTime = seckillVoucherCreateDTO.getBeginTime();
        LocalDateTime endTime = seckillVoucherCreateDTO.getEndTime();

        if (!beginTime.isBefore(endTime)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "开始时间必须早于结束时间");
        }

        if (seckillVoucherCreateDTO.getStock() > vouchers.getStock()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "秒杀卷库存超过优惠卷库存");
        }

        SeckillVouchers seckillVouchers = new SeckillVouchers();
        BeanUtils.copyProperties(seckillVoucherCreateDTO, seckillVouchers);
        seckillVouchers.setStatus(SeckillActivityStatus.DRAFT);

        try {
            if (seckillVouchersMapper.insert(seckillVouchers) != 1) {
                throw new BusinessException(ErrorCode.DB_ERROR, "数据库操作失败");
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.RESOURCE_EXISTS, "该优惠券已经创建过秒杀活动");
        }
    }

    /**
     * 发布秒杀活动：校验权限与时间后，把活动置为「发布中」并写发件箱事件，
     * 真正的 Redis 初始化与上线由定时任务消费发件箱事件完成。
     */
    @Override
    public void publishSeckillVoucher(Long voucherId) {
        Vouchers vouchers = vouchersMapper.selectById(voucherId);

        if (vouchers == null) {
            throw new BusinessException(ErrorCode.VOUCHER_NOT_FOUND, "优惠卷不存在");
        }

        Long userId = SecurityUserContext.requireUserId();

        User user = userMapper.selectById(userId);

        if (user == null) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION, "非法操作");
        }

        Shop shop = shopMapper.selectById(vouchers.getShopId());

        if (shop == null) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION, "商店不存在");
        }

        boolean isADMIN = SecurityUserContext.hasRole(ADMIN_ROLE);
        boolean isMERCHANT = SecurityUserContext.hasRole(MERCHANT_ROLE);

        if (!isADMIN && !isMERCHANT) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION, "无权操作发布活动");
        }

        if (isMERCHANT && !Objects.equals(shop.getOwnerId(), userId)) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION, "无权操作该店铺");
        }

        SeckillVouchers seckillVouchers = seckillVouchersMapper.selectById(voucherId);

        if (seckillVouchers == null) {
            throw new BusinessException(ErrorCode.VOUCHER_NOT_FOUND, "不存在秒杀卷配置");
        }

        if (!SeckillActivityStatus.DRAFT.equals(seckillVouchers.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION, "秒杀卷已发布");
        }

        LocalDateTime now = LocalDateTime.now();

        if (now.isAfter(seckillVouchers.getEndTime())) {
            throw new BusinessException(ErrorCode.SECKILL_OUT_OF_TIME);
        }

        seckillPublishCommandService.startPublish(voucherId);

    }

    private Shop requireWritableShop(Long shopId, Long userId) {
        if (shopId == null) {
            throw new BusinessException(ErrorCode.SHOP_NOT_FOUND, "店铺不存在");
        }

        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            throw new BusinessException(ErrorCode.SHOP_NOT_FOUND, "店铺不存在");
        }

        boolean isAdmin = SecurityUserContext.hasRole(ADMIN_ROLE);
        boolean isMerchant = SecurityUserContext.hasRole(MERCHANT_ROLE);
        if (!isAdmin && !isMerchant) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作优惠券");
        }
        if (isMerchant && !Objects.equals(shop.getOwnerId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该店铺的优惠券");
        }
        return shop;
    }

    private void ensurePatchHasField(VoucherUpdateDTO request) {
        boolean hasField = request.getTitle() != null
                || request.getSubTitle() != null
                || request.getDiscountAmount() != null
                || request.getPayValue() != null
                || request.getStock() != null
                || request.getBeginTime() != null
                || request.getEndTime() != null;
        if (!hasField) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    "至少提供一个需要修改的字段"
            );
        }
        if (request.getTitle() != null && !StringUtils.hasText(request.getTitle())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "优惠券标题不能为空");
        }
    }

    private void applyVoucherPatch(Vouchers voucher, VoucherUpdateDTO request) {
        if (request.getTitle() != null) {
            voucher.setTitle(request.getTitle().trim());
        }
        if (request.getSubTitle() != null) {
            voucher.setSubTitle(request.getSubTitle().trim());
        }
        if (request.getDiscountAmount() != null) {
            voucher.setDiscountAmount(request.getDiscountAmount());
        }
        if (request.getPayValue() != null) {
            voucher.setPayValue(request.getPayValue());
        }
        if (request.getStock() != null) {
            voucher.setStock(request.getStock());
        }
        if (request.getBeginTime() != null) {
            voucher.setBeginTime(request.getBeginTime());
        }
        if (request.getEndTime() != null) {
            voucher.setEndTime(request.getEndTime());
        }
    }

    private void validateMoneyAndPeriod(
            java.math.BigDecimal discountAmount,
            java.math.BigDecimal payValue,
            LocalDateTime beginTime,
            LocalDateTime endTime
    ) {
        if (discountAmount == null || payValue == null
                || discountAmount.signum() < 0
                || payValue.signum() < 0
                || payValue.compareTo(discountAmount) > 0) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    "售价不能高于优惠金额，金额不能为负数"
            );
        }
        if ((beginTime == null) != (endTime == null)
                || beginTime != null && !beginTime.isBefore(endTime)) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    "优惠券开始时间必须早于结束时间，且需要同时传入"
            );
        }
    }

    private void registerAfterCommit(Runnable callback) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            callback.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        callback.run();
                    }
                }
        );
    }
}
