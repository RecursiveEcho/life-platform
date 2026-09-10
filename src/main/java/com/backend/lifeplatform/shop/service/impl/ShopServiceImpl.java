package com.backend.lifeplatform.shop.service.impl;

import com.backend.lifeplatform.common.enums.ErrorCode;
import com.backend.lifeplatform.common.exception.BusinessException;
import com.backend.lifeplatform.common.result.PageResult;
import com.backend.lifeplatform.common.utils.CacheKeyUtils;
import com.backend.lifeplatform.common.utils.RedisDistributedLock;
import com.backend.lifeplatform.common.utils.RedisJsonCacheTool;
import com.backend.lifeplatform.common.utils.SecurityUserContext;
import com.backend.lifeplatform.shop.dto.ShopCreateDTO;
import com.backend.lifeplatform.shop.dto.ShopUpdateDTO;
import com.backend.lifeplatform.shop.entity.Shop;
import com.backend.lifeplatform.shop.mapper.ShopMapper;
import com.backend.lifeplatform.shop.service.ShopService;
import com.backend.lifeplatform.shop.vo.ShopVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.backend.lifeplatform.common.utils.RedisDistributedLock.generateInstanceId;

/**
 * 店铺业务实现。
 *
 * <p>文件按“对外接口 -> 查询辅助 -> 写入与权限 -> 事务和缓存”分组，
 * 这样从控制器方法名进入后，可以在同一个文件中顺着找到完整处理链路。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    // ==================== 角色和店铺状态 ====================

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String MERCHANT_ROLE = "MERCHANT";
    private static final String AUDIT_PENDING = "PENDING";
    private static final String AUDIT_APPROVED = "APPROVED";
    private static final String BUSINESS_OPEN = "OPEN";

    // ==================== 缓存 key ====================

    private static final String CACHE_SHOP_LIST = "cache:shop:list:";
    private static final String CACHE_SHOP_LIST_VERSION = "cache:shop:list:ver:";
    private static final String CACHE_SHOP_DETAIL = "cache:shop:detail:";
    private static final TypeReference<PageResult<ShopVO>> SHOP_LIST_TYPE = new TypeReference<>() {
    };

    // ==================== 依赖 ====================

    private final ShopMapper shopMapper;
    private final RedisJsonCacheTool redisJsonCacheTool;
    private final RedisDistributedLock redisDistributedLock;

    // ==================== 对外业务方法 ====================

    /** 查询已审核且营业中的店铺列表。 */
    @Override
    public PageResult<ShopVO> list(Long typeId, String keyword, Long current, Long size) {
        // 列表缓存使用版本号，店铺变化时只递增版本，不需要逐页删除旧 key。
        String typeSegment = typeId == null ? "all" : typeId.toString();
        String filterSegment = CacheKeyUtils.listFilterSegment(typeSegment, keyword);
        String versionKey = CACHE_SHOP_LIST_VERSION + typeSegment;
        String version = redisJsonCacheTool.getOrInitializeListCacheVersion(versionKey);
        String redisKey = redisJsonCacheTool.buildVersionedListPageKey(
                CACHE_SHOP_LIST + filterSegment, version, current, size);

        return redisJsonCacheTool.getObject(
                redisKey,
                SHOP_LIST_TYPE,
                () -> queryFromDatabase(typeId, keyword, current, size));
    }

    /** 查询店铺详情；游客只能看到已审核且营业中的店铺。 */
    @Override
    public ShopVO getById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "店铺 id 不能为空");
        }

        String redisKey = CACHE_SHOP_DETAIL + id;
        Shop shop = redisJsonCacheTool.getObject(
                redisKey,
                Shop.class,
                () -> shopMapper.selectOne(new LambdaQueryWrapper<Shop>()
                        .eq(Shop::getId, id)
                        .eq(Shop::getAuditStatus, AUDIT_APPROVED)
                        .eq(Shop::getBusinessStatus, BUSINESS_OPEN)
                        .eq(Shop::getDeleted, 0)));
        if (shop == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "店铺不存在");
        }
        return toVO(shop);
    }

    /** 创建店铺；商家创建后进入待审核，管理员创建后直接通过。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createShop(ShopCreateDTO shopDTO) {
        Shop shop = new Shop();
        BeanUtils.copyProperties(shopDTO, shop);

        if (SecurityUserContext.hasRole(MERCHANT_ROLE)) {
            shop.setOwnerId(SecurityUserContext.requireUserId());
            shop.setAuditStatus(AUDIT_PENDING);
        } else if (SecurityUserContext.hasRole(ADMIN_ROLE)) {
            shop.setAuditStatus(AUDIT_APPROVED);
        }
        shop.setBusinessStatus(BUSINESS_OPEN);
        shop.setDeleted(0);

        if (shopMapper.insert(shop) != 1 || shop.getId() == null) {
            throw new BusinessException(ErrorCode.DB_ERROR, "店铺创建失败");
        }

        // 事务提交后再递增列表版本，回滚时不会让缓存提前失效。
        registerAfterCommit(() -> {
            bumpPublishedListCacheVersion("all");
            bumpPublishedListCacheVersion(String.valueOf(shop.getTypeId()));
        });
        return shop.getId();
    }

    /** 修改店铺信息（PATCH 语义，只更新请求体中实际传入的字段）。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateShop(Long id, ShopUpdateDTO shopDTO) {
        validateUpdateRequest(shopDTO);

        String lockKey = "lock:update:shop:" + id;
        String lockInstance = generateInstanceId();
        if (!redisDistributedLock.tryLock(lockKey, lockInstance)) {
            throw new BusinessException(ErrorCode.SYSTEM_BUSY, "店铺正在被修改，请稍后再试");
        }

        boolean callbackRegistered = false;
        try {
            Shop shop = shopMapper.selectById(id);
            if (shop == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "店铺信息不存在");
            }

            checkShopOwnership(shop);
            Long oldTypeId = shop.getTypeId();
            applyUpdate(shop, shopDTO);
            // id 只能使用路径参数，不能被请求体中的字段覆盖。
            shop.setId(id);

            if (shopMapper.updateById(shop) != 1) {
                throw new BusinessException(ErrorCode.DB_ERROR, "店铺修改失败");
            }

            Long newTypeId = shop.getTypeId();
            // 锁持有到事务完成，避免事务回滚前释放锁导致并发写入交错。
            registerAfterCommit(
                    () -> invalidateShopCaches(id, oldTypeId, newTypeId),
                    () -> redisDistributedLock.unlock(lockKey, lockInstance));
            callbackRegistered = true;
        } finally {
            // 注册事务回调前发生异常时，必须立即释放锁。
            if (!callbackRegistered) {
                redisDistributedLock.unlock(lockKey, lockInstance);
            }
        }
    }

    // ==================== 查询辅助 ====================

    /** 从数据库分页查询店铺，并转换成接口返回的 VO。 */
    private PageResult<ShopVO> queryFromDatabase(
            Long typeId, String keyword, Long current, Long size) {
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<Shop>()
                .eq(typeId != null, Shop::getTypeId, typeId)
                .like(StringUtils.hasText(keyword), Shop::getName, keyword)
                .eq(Shop::getBusinessStatus, BUSINESS_OPEN)
                .eq(Shop::getAuditStatus, AUDIT_APPROVED)
                .eq(Shop::getDeleted, 0)
                .orderByDesc(Shop::getId);
        IPage<Shop> page = shopMapper.selectPage(new Page<>(current, size), wrapper);
        List<ShopVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 将数据库实体转换为接口返回对象，避免把内部状态字段直接暴露给游客。 */
    private ShopVO toVO(Shop entity) {
        ShopVO vo = new ShopVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    // ==================== 写入和权限辅助 ====================

    /** 校验 PATCH 请求至少包含一个字段，并检查字段内容。 */
    private void validateUpdateRequest(ShopUpdateDTO shopDTO) {
        boolean hasUpdate = shopDTO.getName() != null
                || shopDTO.getTypeId() != null
                || shopDTO.getAddress() != null
                || shopDTO.getLongitude() != null
                || shopDTO.getLatitude() != null
                || shopDTO.getAvgPrice() != null;
        if (!hasUpdate) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "至少提供一个需要修改的字段");
        }
        if (shopDTO.getName() != null && !StringUtils.hasText(shopDTO.getName())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "店铺名称不能为空");
        }
        if (shopDTO.getAddress() != null && !StringUtils.hasText(shopDTO.getAddress())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "店铺地址不能为空");
        }
        if (shopDTO.getAvgPrice() != null && shopDTO.getAvgPrice().signum() < 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "人均消费不能为负数");
        }
    }

    /** 把 PATCH 请求中非 null 的字段应用到实体，未传字段保持原值。 */
    private void applyUpdate(Shop shop, ShopUpdateDTO shopDTO) {
        if (shopDTO.getName() != null) {
            shop.setName(shopDTO.getName());
        }
        if (shopDTO.getTypeId() != null) {
            shop.setTypeId(shopDTO.getTypeId());
        }
        if (shopDTO.getAddress() != null) {
            shop.setAddress(shopDTO.getAddress());
        }
        if (shopDTO.getLongitude() != null) {
            shop.setLongitude(shopDTO.getLongitude());
        }
        if (shopDTO.getLatitude() != null) {
            shop.setLatitude(shopDTO.getLatitude());
        }
        if (shopDTO.getAvgPrice() != null) {
            shop.setAvgPrice(shopDTO.getAvgPrice());
        }
    }

    /** 管理员可以修改任意店铺，商家只能修改自己拥有的店铺。 */
    private void checkShopOwnership(Shop shop) {
        if (SecurityUserContext.hasRole(ADMIN_ROLE)) {
            return;
        }

        Long currentUserId = SecurityUserContext.requireUserId();
        if (!SecurityUserContext.hasRole(MERCHANT_ROLE)
                || shop.getOwnerId() == null
                || !shop.getOwnerId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION, "无权操作该店铺");
        }
    }

    // ==================== 事务和缓存辅助 ====================

    /** 事务未开启时立即执行，否则在事务提交成功后执行。 */
    private void registerAfterCommit(Runnable afterCommit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            afterCommit.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                afterCommit.run();
            }
        });
    }

    /** 注册提交回调和事务完成回调，后者通常用于释放分布式锁。 */
    private void registerAfterCommit(Runnable afterCommit, Runnable afterCompletion) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            try {
                afterCommit.run();
            } finally {
                afterCompletion.run();
            }
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                afterCommit.run();
            }

            @Override
            public void afterCompletion(int status) {
                afterCompletion.run();
            }
        });
    }

    /** 失效详情缓存，并递增全量列表和新旧分类列表的版本号。 */
    private void invalidateShopCaches(Long id, Long oldTypeId, Long newTypeId) {
        deleteCacheQuietly(CACHE_SHOP_DETAIL + id);
        bumpPublishedListCacheVersion("all");
        if (oldTypeId != null) {
            bumpPublishedListCacheVersion(String.valueOf(oldTypeId));
        }
        if (newTypeId != null && !newTypeId.equals(oldTypeId)) {
            bumpPublishedListCacheVersion(String.valueOf(newTypeId));
        }
    }

    /** 递增指定分类的列表缓存版本。 */
    private void bumpPublishedListCacheVersion(String typeSegment) {
        redisJsonCacheTool.bumpListCacheVersion(CACHE_SHOP_LIST_VERSION + typeSegment);
    }

    /** 删除详情缓存；缓存删除失败只记录日志，不影响数据库事务。 */
    private void deleteCacheQuietly(String key) {
        try {
            redisJsonCacheTool.delete(key);
        } catch (RuntimeException e) {
            log.error("删除店铺缓存失败，key={}", key, e);
        }
    }
}

