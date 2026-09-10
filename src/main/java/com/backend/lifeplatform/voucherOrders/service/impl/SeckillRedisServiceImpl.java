package com.backend.lifeplatform.voucherOrders.service.impl;

import com.backend.lifeplatform.common.enums.ErrorCode;
import com.backend.lifeplatform.common.exception.BusinessException;
import com.backend.lifeplatform.voucherOrders.service.SeckillRedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 基于 Lua 脚本实现秒杀入口的原子预扣。
 *
 * <p>库存 String 和已抢用户 Set 必须在同一次 Redis 执行中检查并修改，
 * 否则“查库存 -> 扣库存 -> 记录用户”之间的并发窗口会造成超卖或重复抢购。</p>
 */
@Service
@RequiredArgsConstructor
public class SeckillRedisServiceImpl implements SeckillRedisService {

    /**
     * Redis 中维护的预扣库存 key 前缀。
     */
    private static final String STOCK_KEY_PREFIX = "seckill:stock:";
    /**
     * Redis Set 中记录已成功预扣用户的 key 前缀。
     */
    private static final String ORDER_USERS_KEY_PREFIX = "seckill:order:users:";
    /** Redis 中记录秒杀活动元信息（起止时间、状态）的 key 前缀。 */
    private static final String ACTIVITY_KEY_PREFIX = "seckill:activity:";


    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    private static final DefaultRedisScript<Long> SECKILL_COMPENSATE_SCRIPT;
    private static final DefaultRedisScript<Long> SECKILL_INITIALIZE_SCRIPT;
    private static final DefaultRedisScript<Long> SECKILL_ACTICATE_SCRIPT;

    static {
        // 预扣脚本返回 0=成功、1=库存不足、2=用户已抢过，业务层再映射成统一错误码。
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(
                new ClassPathResource("lua/seckill.lua")
        );
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    static {
        // 补偿脚本先移除用户标记，只有移除成功时才把库存加回，保证重复补偿不会增库存。
        SECKILL_COMPENSATE_SCRIPT = new DefaultRedisScript<>();
        SECKILL_COMPENSATE_SCRIPT.setLocation(
                new ClassPathResource("lua/seckill_compensate.lua")
        );
        SECKILL_COMPENSATE_SCRIPT.setResultType(Long.class);
    }

    // 初始化脚本：写入活动元信息与库存，仅在活动不存在时执行，保证发布流程幂等。
    static {
        SECKILL_INITIALIZE_SCRIPT = new DefaultRedisScript<>();
        SECKILL_INITIALIZE_SCRIPT.setLocation(
                new ClassPathResource("lua/seckill_initialize.lua")
        );
        SECKILL_INITIALIZE_SCRIPT.setResultType(Long.class);
    }

    // 激活脚本：把活动置为可抢购状态，秒杀脚本据此放行抢购请求。
    static {
        SECKILL_ACTICATE_SCRIPT = new DefaultRedisScript<>();
        SECKILL_ACTICATE_SCRIPT.setLocation(
                new ClassPathResource("lua/seckill_activate.lua")
        );
        SECKILL_ACTICATE_SCRIPT.setResultType(Long.class);
    }


    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 原子预扣库存和用户抢购资格。
     */
    @Override
    public void preDeduct(Long voucherId, Long userId) {
        String activityKey = ACTIVITY_KEY_PREFIX + voucherId;
        String stockKey = STOCK_KEY_PREFIX + voucherId;
        String orderUsersKey = ORDER_USERS_KEY_PREFIX + voucherId;

        // KEYS[1]=活动元信息、KEYS[2]=库存、KEYS[3]=用户 Set，ARGV[1]=当前用户 id。
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                List.of(activityKey, stockKey, orderUsersKey),
                userId.toString());

        if (result == null) {
            throw new BusinessException(
                    ErrorCode.CACHE_ERROR,
                    "秒杀资格校验失败"
            );
        }

        if (result == 1L) {
            throw new BusinessException(
                    ErrorCode.VOUCHER_SOLD_OUT,
                    "秒杀券已抢光"
            );
        }

        if (result == 2L) {
            throw new BusinessException(
                    ErrorCode.VOUCHER_ALREADY_GRABBED,
                    "您已抢过该优惠券"
            );
        }

        if (result == 3L) {
            throw new BusinessException(
                    ErrorCode.VOUCHER_NOT_AVAILABLE,
                    "秒杀活动不存在或尚未初始化"
            );
        }

        if (result == 4L) {
            throw new BusinessException(
                    ErrorCode.VOUCHER_NOT_AVAILABLE,
                    "秒杀活动未发布或已下线"
            );
        }

        if (result == 5L) {
            throw new BusinessException(
                    ErrorCode.SECKILL_OUT_OF_TIME,
                    "秒杀活动尚未开始"
            );
        }

        if (result == 6L) {
            throw new BusinessException(
                    ErrorCode.SECKILL_OUT_OF_TIME,
                    "秒杀活动已结束"
            );
        }

        if (result != 0L) {
            throw new BusinessException(ErrorCode.CACHE_ERROR,
                    "秒杀资格校验返回未知结果");
        }
    }

    @Override
    public boolean ensureActivity(
            Long voucherId,
            Integer stock,
            LocalDateTime beginTime,
            LocalDateTime endTime
    ) {
        if (voucherId == null
                || stock == null
                || stock <= 0
                || beginTime == null
                || endTime == null
                || !beginTime.isBefore(endTime)) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    "秒杀活动初始化参数无效"
            );
        }

        String activityKey = ACTIVITY_KEY_PREFIX + voucherId;
        String stockKey = STOCK_KEY_PREFIX + voucherId;
        String orderUserKey = ORDER_USERS_KEY_PREFIX + voucherId;

        // 把起止时间转成毫秒时间戳传给 Lua，脚本内用当前时间戳判断活动时间窗口。
        long beginTimeMillis = beginTime
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        long endTimeMills = endTime
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        Long result = stringRedisTemplate.execute(
                SECKILL_INITIALIZE_SCRIPT,
                List.of(activityKey, stockKey, orderUserKey),
                stock.toString(),
                String.valueOf(beginTimeMillis),
                String.valueOf(endTimeMills)
        );

        if (result == null || result == 0L) {
            throw new BusinessException(
                    ErrorCode.CACHE_ERROR,
                    "秒杀活动Redis数据冲突"
            );
        }

        return result == 1L;
    }

    /** 把 Redis 中已初始化的秒杀活动激活为可抢购状态。 */
    @Override
    public void activateActivity(Long voucherId) {

        if (voucherId == null) {
            throw new BusinessException(
                    ErrorCode.PARAM_INVALID,
                    "优惠卷ID不能为空"
            );
        }

        Long result = stringRedisTemplate.execute(
                SECKILL_ACTICATE_SCRIPT,
                List.of(ACTIVITY_KEY_PREFIX + voucherId)
        );

        if (result == null || result != 1L) {
            throw new BusinessException(
                    ErrorCode.CACHE_ERROR,
                    "秒杀活动激活失败"
            );
        }
    }

    /**
     * 撤销 Redis 预扣，供发送失败和死信补偿路径调用。
     */
    @Override
    public void compensatePreDeduct(Long voucherId, Long userId) {
        String stockKey = STOCK_KEY_PREFIX + voucherId;
        String orderUsersKey = ORDER_USERS_KEY_PREFIX + voucherId;

        // 死信或消息投递失败时撤销预扣；脚本自身保证补偿操作可幂等执行。
        Long result = stringRedisTemplate.execute(SECKILL_COMPENSATE_SCRIPT,
                List.of(stockKey, orderUsersKey),
                userId.toString()
        );


        if (result == null) {
            throw new BusinessException(ErrorCode.CACHE_ERROR, "秒杀预扣库存补偿失败");
        }
    }


}
