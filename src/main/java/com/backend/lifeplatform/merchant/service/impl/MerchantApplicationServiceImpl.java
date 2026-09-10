package com.backend.lifeplatform.merchant.service.impl;

import com.backend.lifeplatform.common.enums.ErrorCode;
import com.backend.lifeplatform.common.exception.BusinessException;
import com.backend.lifeplatform.common.utils.RedisJsonCacheTool;
import com.backend.lifeplatform.common.utils.SecurityUserContext;
import com.backend.lifeplatform.merchant.dto.MerchantApplyDTO;
import com.backend.lifeplatform.merchant.entity.MerchantApplication;
import com.backend.lifeplatform.merchant.mapper.MerchantApplicationMapper;
import com.backend.lifeplatform.merchant.service.MerchantApplicationService;
import com.backend.lifeplatform.merchant.vo.MerchantApplicationVO;
import com.backend.lifeplatform.user.entity.User;
import com.backend.lifeplatform.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商家入驻申请业务实现。
 *
 * <p>文件按“提交 -> 审批 -> 查询 -> 内部辅助”分组，审批通过时把用户角色和申请状态
 * 放在同一个事务中更新，便于沿着一个业务动作阅读完整流程。</p>
 */
@Service
@RequiredArgsConstructor
public class MerchantApplicationServiceImpl implements MerchantApplicationService {

    // ==================== 角色和申请状态 ====================

    private static final String USER_ROLE = "USER";
    private static final String MERCHANT_ROLE = "MERCHANT";
    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";

    // ==================== 缓存 key ====================

    private static final String CACHE_APPLICATION_DETAIL = "cache:application:detail";

    // ==================== 依赖 ====================

    private final MerchantApplicationMapper merchantApplicationMapper;
    private final UserMapper userMapper;
    private final RedisJsonCacheTool redisJsonCacheTool;

    // ==================== 提交和审批 ====================

    /** 提交商家入驻申请；只有普通用户可以申请，且不能重复提交待审核申请。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void apply(MerchantApplyDTO merchantApplyDTO) {
        Long userId = SecurityUserContext.requireUserId();
        User user = getUser(userId);
        if (!USER_ROLE.equals(user.getRole())) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION, "当前用户无法申请为商家");
        }

        Long pendingCount = merchantApplicationMapper.selectCount(new LambdaQueryWrapper<MerchantApplication>()
                .eq(MerchantApplication::getUserId, userId)
                .eq(MerchantApplication::getStatus, PENDING));
        if (pendingCount > 0) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION, "当前用户无法申请为商家");
        }

        MerchantApplication application = new MerchantApplication();
        BeanUtils.copyProperties(merchantApplyDTO, application);
        application.setUserId(userId);
        application.setStatus(PENDING);
        if (merchantApplicationMapper.insert(application) != 1) {
            throw new BusinessException(ErrorCode.DB_ERROR, "商家申请提交失败");
        }
    }

    /** 审批通过申请，并把对应用户角色提升为商家。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long applicationId) {
        MerchantApplication application = getApplication(applicationId);
        if (!PENDING.equals(application.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION, "该申请已经处理过了");
        }

        User user = getUser(application.getUserId());
        user.setRole(MERCHANT_ROLE);
        if (userMapper.updateById(user) != 1) {
            throw new BusinessException(ErrorCode.DB_ERROR, "商家角色更新失败");
        }

        application.setStatus(APPROVED);
        application.setReviewedBy(SecurityUserContext.requireUserId());
        application.setUpdateTime(LocalDateTime.now());
        application.setReviewedAt(LocalDateTime.now());
        if (merchantApplicationMapper.updateById(application) != 1) {
            throw new BusinessException(ErrorCode.DB_ERROR, "商家申请更新失败");
        }
    }

    /** 驳回申请，并记录管理员填写的驳回原因。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long applicationId, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "驳回原因不能为空");
        }

        MerchantApplication application = getApplication(applicationId);
        if (!PENDING.equals(application.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION, "该申请已经处理过了");
        }

        application.setStatus(REJECTED);
        application.setRejectReason(reason);
        application.setReviewedBy(SecurityUserContext.requireUserId());
        application.setUpdateTime(LocalDateTime.now());
        application.setReviewedAt(LocalDateTime.now());
        if (merchantApplicationMapper.updateById(application) != 1) {
            throw new BusinessException(ErrorCode.DB_ERROR, "商家申请更新失败");
        }
    }

    // ==================== 查询 ====================

    /** 查询申请列表，可按 PENDING、APPROVED、REJECTED 筛选。 */
    @Override
    public List<MerchantApplicationVO> list(String status) {
        LambdaQueryWrapper<MerchantApplication> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(MerchantApplication::getStatus, status);
        }
        List<MerchantApplication> applications = merchantApplicationMapper.selectList(wrapper);
        return applications.stream().map(this::toVO).toList();
    }

    /** 查询申请详情，优先读取 Redis，未命中时回源数据库。 */
    @Override
    public MerchantApplicationVO detail(Long applicationId) {
        String redisKey = CACHE_APPLICATION_DETAIL + applicationId;
        MerchantApplication application = redisJsonCacheTool.getObject(
                redisKey,
                MerchantApplication.class,
                () -> merchantApplicationMapper.selectById(applicationId));
        if (application == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "申请不存在");
        }
        return toVO(application);
    }

    // ==================== 内部查询和更新辅助 ====================

    /** 按主键查询申请，不存在时抛出资源不存在异常。 */
    private MerchantApplication getApplication(Long applicationId) {
        MerchantApplication application = merchantApplicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "商家申请不存在");
        }
        return application;
    }

    /** 按主键查询用户，不存在时抛出用户不存在异常。 */
    private User getUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    /** 将申请实体转换为接口返回对象。 */
    private MerchantApplicationVO toVO(MerchantApplication application) {
        MerchantApplicationVO vo = new MerchantApplicationVO();
        BeanUtils.copyProperties(application, vo);
        return vo;
    }
}
