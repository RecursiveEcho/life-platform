package com.backend.lifeplatform.common.utils;

import com.backend.lifeplatform.common.enums.ErrorCode;
import com.backend.lifeplatform.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 基于固定时间窗口的简单限流器。 */
@Component
@RequiredArgsConstructor
public class RedisRateLimiter {

    private static final long WINDOW_SECONDS = 60;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 固定时间窗口限流。
     *
     * <p>原理：对 key 做 INCR，首次计数（count==1）时才设置过期时间，这样能保证
     * 一个时间窗口内的所有请求共享同一个过期起点。超过 maxRequests 即拒绝。</p>
     */
    public void check(String key, int maxRequests) {
        Long count = stringRedisTemplate.opsForValue().increment(key);
        // 只有第一次请求才设置 TTL，否则每次 INCR 都会刷新过期时间，窗口就永远不结束了。
        if (count != null && count == 1) {
            stringRedisTemplate.expire(key, Duration.ofSeconds(WINDOW_SECONDS));
        }
        if (count != null && count > maxRequests) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
    }
}
