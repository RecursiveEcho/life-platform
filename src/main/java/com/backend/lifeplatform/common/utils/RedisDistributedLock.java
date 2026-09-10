package com.backend.lifeplatform.common.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 基于 Redis 的简单分布式锁。
 *
 * <p>加锁使用 SET NX，并设置过期时间；解锁使用 Lua 脚本，只有持有锁的实例才能删除锁。</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RedisDistributedLock {

    private static final Duration DEFAULT_TTL = Duration.ofSeconds(10);

    private static final String UNLOCK_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;

    private static final DefaultRedisScript<Long> UNLOCK =
            new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    public boolean tryLock(String lockKey, String instanceId) {
        return tryLock(lockKey, instanceId, DEFAULT_TTL);
    }

    /**
     * 尝试加锁。
     *
     * <p>value 存 instanceId（每个调用方唯一的随机串），解锁时靠它判断“这把锁是不是自己加的”，
     * 防止误删别人的锁。</p>
     */
    public boolean tryLock(String lockKey, String instanceId, Duration ttl) {
        Duration actualTtl = ttl == null ? DEFAULT_TTL : ttl;
        // SET NX EX：仅当 key 不存在时设置并带上过期时间，两个动作原子完成。
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, instanceId, actualTtl);
        boolean acquired = Boolean.TRUE.equals(locked);
        log.debug("distributed lock {}: {}", acquired ? "acquired" : "not acquired", lockKey);
        return acquired;
    }

    /**
     * 释放锁。
     *
     * <p>必须用 Lua 脚本原子地“先判断 value 是否等于自己的 instanceId，再删除”，
     * 否则若锁已过期被他人持有，直接 DEL 会误删他人的锁。</p>
     */
    public void unlock(String lockKey, String instanceId) {
        Long deleted = stringRedisTemplate.execute(
                UNLOCK,
                Collections.singletonList(lockKey),
                instanceId);
        if (Long.valueOf(1L).equals(deleted)) {
            log.debug("distributed lock released: {}", lockKey);
        }
    }

    public static String generateInstanceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
