package com.backend.lifeplatform.common.utils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link RedisJsonCacheTool} 的单元测试（用 Mockito 模拟 Redis 与 ObjectMapper，不依赖真实 Redis）。 */
@ExtendWith(MockitoExtension.class)
class RedisJsonCacheToolTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RedisJsonCacheTool redisJsonCacheTool;

    @Test
    void getObject_whenCacheMiss_returnsNull() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("shop:list")).thenReturn(null);

        ShopCacheValue actual =
                redisJsonCacheTool.getObject("shop:list", ShopCacheValue.class);

        assertThat(actual).isNull();
    }

    @Test
    void getObject_whenCacheHit_returnsObject() throws Exception {
        ShopCacheValue expected = new ShopCacheValue(1L, "咖啡店");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("shop:list")).thenReturn("{\"id\":1,\"name\":\"咖啡店\"}");
        when(objectMapper.readValue(
                "{\"id\":1,\"name\":\"咖啡店\"}", ShopCacheValue.class)).thenReturn(expected);

        ShopCacheValue actual =
                redisJsonCacheTool.getObject("shop:list", ShopCacheValue.class);

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void getObject_whenJsonIsInvalid_deletesBrokenCache() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("shop:list")).thenReturn("{broken");
        when(objectMapper.readValue("{broken", ShopCacheValue.class))
                .thenThrow(new JsonParseException(null, "invalid json"));

        ShopCacheValue actual =
                redisJsonCacheTool.getObject("shop:list", ShopCacheValue.class);

        assertThat(actual).isNull();
        verify(stringRedisTemplate).delete("shop:list");
    }

    @Test
    void setObject_writesJsonWithTtl() throws Exception {
        ShopCacheValue value = new ShopCacheValue(1L, "咖啡店");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(objectMapper.writeValueAsString(value))
                .thenReturn("{\"id\":1,\"name\":\"咖啡店\"}");

        redisJsonCacheTool.setObject("shop:1", value, Duration.ofMinutes(5));

        verify(valueOperations).set(
                "shop:1", "{\"id\":1,\"name\":\"咖啡店\"}",
                Duration.ofMinutes(5).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Test
    void setNullMarker_writesMarkerWithTtl() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        redisJsonCacheTool.setNullMarker("shop:404", Duration.ofMinutes(2));

        verify(valueOperations).set(
                "shop:404", "_NULL_",
                Duration.ofMinutes(2).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private record ShopCacheValue(Long id, String name) {
    }
}
