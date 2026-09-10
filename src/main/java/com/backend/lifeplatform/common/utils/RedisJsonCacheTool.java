package com.backend.lifeplatform.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redis JSON 缓存工具。
 *
 * <p>文件按“读缓存 -> 写缓存 -> 列表版本 -> 空值占位 -> 参数校验”分组，
 * 读缓存的回源、分布式锁和 JSON 转换放在同一组，方便顺着一条缓存流程阅读。</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RedisJsonCacheTool {

    // ==================== 常量、依赖和配置 ====================

    /** 空值占位符：区分“数据不存在”和“缓存未命中”，防止缓存穿透。 */
    private static final String NULL_MARKER = "_NULL_";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisDistributedLock redisDistributedLock;

    @Value("${cache.ttl.detail-minutes:5}")
    private long defaultTtlMinutes;

    @Value("${cache.ttl.detail-jitter-seconds:300}")
    private long detailJitterSeconds;

    @Value("${cache.ttl.null-marker-minutes:2}")
    private long nullMarkerTtlMinutes;

    @Value("${cache.ttl.list-minutes:10}")
    private long listTtlMinutes;

    @Value("${cache.lock-retry.max-count:20}")
    private int maxLockRetryCount = 20;

    @Value("${cache.lock-retry.interval-millis:50}")
    private long lockRetryIntervalMillis = 50L;

    // ==================== 读缓存 ====================

    /** 直接读取并反序列化普通类型。 */
    public <T> T getObject(String key, Class<T> type) {
        String redisKey = requireKey(key);
        Class<T> targetType = Objects.requireNonNull(type, "target type must not be null");
        String cached = readRaw(redisKey);
        if (!isUsableCacheValue(cached)) {
            return null;
        }
        try {
            log.debug("redis cache hit, key={}", redisKey);
            return objectMapper.readValue(cached, targetType);
        } catch (JsonProcessingException e) {
            return deleteBrokenCache(redisKey, e);
        }
    }

    /** 直接读取并反序列化带泛型的类型。 */
    public <T> T getObject(String key, TypeReference<T> typeReference) {
        String redisKey = requireKey(key);
        TypeReference<T> targetType = Objects.requireNonNull(
                typeReference, "target type must not be null");
        String cached = readRaw(redisKey);
        if (!isUsableCacheValue(cached)) {
            return null;
        }
        try {
            log.debug("redis cache hit, key={}", redisKey);
            return objectMapper.readValue(cached, targetType);
        } catch (JsonProcessingException e) {
            return deleteBrokenCache(redisKey, e);
        }
    }

    /** 读取普通类型缓存，未命中时用分布式锁保护 loader 并回填缓存。 */
    public <T> T getObject(String key, Class<T> type, Supplier<T> loader) {
        return getObjectWithLoader(
                key, (cached, cacheKey) -> readValue(cached, type, cacheKey), loader);
    }

    /** 读取泛型缓存，未命中时用分布式锁保护 loader 并回填缓存。 */
    public <T> T getObject(String key, TypeReference<T> typeReference, Supplier<T> loader) {
        TypeReference<T> targetType = Objects.requireNonNull(
                typeReference, "target type must not be null");
        return getObjectWithLoader(
                key, (cached, cacheKey) -> readValue(cached, targetType, cacheKey), loader);
    }

    /**
     * 读取缓存，未命中时用 Redis 分布式锁串行化 loader 调用。
     *
     * <p>热点 key 过期后，只让抢到锁的一个请求查询数据库并回填缓存，
     * 其他请求在锁外短暂等待后重读缓存，避免缓存击穿。</p>
     */
    private <T> T getObjectWithLoader(
            String key, CacheValueReader<T> reader, Supplier<T> loader) {
        String redisKey = requireKey(key);
        Objects.requireNonNull(loader, "cache loader must not be null");

        T cached = readCachedValue(redisKey, reader);
        if (cached != null) {
            return cached;
        }
        // 已有空值占位时直接返回，避免每次都回源数据库。
        if (isNullMarker(redisKey)) {
            return null;
        }

        String lockKey = "mutex:" + redisKey;
        String instanceId = RedisDistributedLock.generateInstanceId();
        for (int retry = 0; retry <= maxLockRetryCount; retry++) {
            if (redisDistributedLock.tryLock(lockKey, instanceId)) {
                try {
                    // 双重检查：等待锁期间，其他线程可能已经构建好缓存。
                    cached = readCachedValue(redisKey, reader);
                    if (cached != null || isNullMarker(redisKey)) {
                        return cached;
                    }

                    T result = loader.get();
                    if (result == null) {
                        // 查询结果确实不存在，写短期占位符防止缓存穿透。
                        setNullMarker(redisKey);
                    } else {
                        setObject(redisKey, result);
                    }
                    return result;
                } finally {
                    redisDistributedLock.unlock(lockKey, instanceId);
                }
            }

            // 没抢到锁时先重读缓存，仍未就绪再等待后重试。
            cached = readCachedValue(redisKey, reader);
            if (cached != null || isNullMarker(redisKey)) {
                return cached;
            }
            sleepBeforeRetry();
        }

        // 重试耗尽后兜底查一次，避免请求无限阻塞。
        log.warn("redis cache lock retry exhausted, key={}", redisKey);
        return loader.get();
    }

    /** 读取原始字符串并判断是否是可反序列化的缓存值。 */
    private <T> T readCachedValue(String key, CacheValueReader<T> reader) {
        String cached = readRaw(key);
        if (!isUsableCacheValue(cached)) {
            return null;
        }
        return reader.read(cached, key);
    }

    private <T> T readValue(String cached, Class<T> type, String key) {
        try {
            return objectMapper.readValue(cached, type);
        } catch (JsonProcessingException e) {
            return deleteBrokenCache(key, e);
        }
    }

    private <T> T readValue(String cached, TypeReference<T> type, String key) {
        try {
            return objectMapper.readValue(cached, type);
        } catch (JsonProcessingException e) {
            return deleteBrokenCache(key, e);
        }
    }

    /** JSON 损坏时删除缓存，让下一次请求重新回源。 */
    private <T> T deleteBrokenCache(String key, JsonProcessingException e) {
        log.warn("redis cache json parse failed, key={}", key, e);
        stringRedisTemplate.delete(key);
        return null;
    }

    private String readRaw(String key) {
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(cached)) {
            log.debug("redis cache miss, key={}", key);
            return null;
        }
        return cached;
    }

    private boolean isUsableCacheValue(String cached) {
        return StringUtils.hasText(cached) && !NULL_MARKER.equals(cached);
    }

    /** 未抢到缓存锁时短暂等待；响应中断并恢复线程中断标记。 */
    private void sleepBeforeRetry() {
        try {
            Thread.sleep(lockRetryIntervalMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for cache lock", e);
        }
    }

    // ==================== 写缓存 ====================

    /** 使用带随机抖动的详情缓存 TTL 写入对象。 */
    public void setObject(String key, Object value) {
        setObject(key, value, buildDetailCacheTtl());
    }

    /** 使用指定 TTL 写入 JSON 缓存。 */
    public void setObject(String key, Object value, Duration ttl) {
        String redisKey = requireKey(key);
        Object cacheValue = Objects.requireNonNull(value, "cache value must not be null");
        Duration cacheTtl = requirePositiveTtl(ttl);
        try {
            String json = objectMapper.writeValueAsString(cacheValue);
            stringRedisTemplate.opsForValue().set(
                    redisKey, json, cacheTtl.toMillis(), TimeUnit.MILLISECONDS);
            log.debug("redis cache set, key={}, ttl={}ms", redisKey, cacheTtl.toMillis());
        } catch (JsonProcessingException e) {
            log.warn("redis cache json write failed, key={}", redisKey, e);
        }
    }

    /** 使用列表缓存 TTL 写入列表对象。 */
    public void setListCacheObject(String key, Object value) {
        setObject(key, value, Duration.ofMinutes(listTtlMinutes));
    }

    /** 详情缓存 TTL = 基础分钟数 + 随机抖动秒数，错峰避免大量 key 同时失效。 */
    private Duration buildDetailCacheTtl() {
        if (detailJitterSeconds <= 0) {
            return Duration.ofMinutes(defaultTtlMinutes);
        }
        long jitter = ThreadLocalRandom.current().nextLong(detailJitterSeconds + 1);
        return Duration.ofMinutes(defaultTtlMinutes).plusSeconds(jitter);
    }

    // ==================== 列表缓存版本 ====================

    /** 读取列表缓存版本；第一次使用时原子初始化。 */
    public String getOrInitializeListCacheVersion(String versionKey) {
        String redisKey = requireKey(versionKey);
        String version = stringRedisTemplate.opsForValue().get(redisKey);
        if (StringUtils.hasText(version)) {
            return version;
        }

        String freshVersion = String.valueOf(System.currentTimeMillis());
        if (Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                .setIfAbsent(redisKey, freshVersion))) {
            return freshVersion;
        }

        String storedVersion = stringRedisTemplate.opsForValue().get(redisKey);
        if (StringUtils.hasText(storedVersion)) {
            return storedVersion;
        }
        throw new IllegalStateException("failed to initialize list cache version, key=" + redisKey);
    }

    /** 递增列表缓存版本，让旧版本 key 自然过期。 */
    public long bumpListCacheVersion(String versionKey) {
        Long version = stringRedisTemplate.opsForValue().increment(requireKey(versionKey));
        return version == null ? 1L : version;
    }

    /** 构建带版本号和分页参数的列表缓存 key。 */
    public String buildVersionedListPageKey(
            String prefix, String version, long current, long size) {
        return Objects.requireNonNull(prefix, "prefix must not be null")
                + Objects.requireNonNull(version, "version must not be null")
                + ":" + current + ":" + size;
    }

    /** 删除指定缓存 key。 */
    public void delete(String key) {
        String redisKey = requireKey(key);
        stringRedisTemplate.delete(redisKey);
        log.debug("redis cache delete, key={}", redisKey);
    }

    // ==================== 空值占位 ====================

    /** 使用默认 TTL 写入空值占位符。 */
    public void setNullMarker(String key) {
        setNullMarker(key, Duration.ofMinutes(nullMarkerTtlMinutes));
    }

    /** 使用指定 TTL 写入空值占位符。 */
    public void setNullMarker(String key, Duration ttl) {
        String redisKey = requireKey(key);
        Duration cacheTtl = requirePositiveTtl(ttl);
        stringRedisTemplate.opsForValue().set(
                redisKey, NULL_MARKER, cacheTtl.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** 判断 key 是否保存的是空值占位符。 */
    public boolean isNullMarker(String key) {
        String cached = stringRedisTemplate.opsForValue().get(requireKey(key));
        return Objects.equals(NULL_MARKER, cached);
    }

    // ==================== 参数校验 ====================

    private String requireKey(String key) {
        return Objects.requireNonNull(key, "redis key must not be null");
    }

    private Duration requirePositiveTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("cache ttl must be greater than zero");
        }
        return ttl;
    }

    @FunctionalInterface
    private interface CacheValueReader<T> {
        T read(String cached, String key);
    }
}
