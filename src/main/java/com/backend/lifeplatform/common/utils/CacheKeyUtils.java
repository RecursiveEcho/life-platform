package com.backend.lifeplatform.common.utils;

import java.util.StringJoiner;

/** Redis 缓存 key 生成工具。 */
public final class CacheKeyUtils {

    /** 禁止实例化。 */
    private CacheKeyUtils() {
    }

    /** 生成详情缓存 key。 */
    public static String detailKey(String prefix, Object id) {
        return prefix + id;
    }

    /**
     * 生成列表分页缓存 key 的筛选维度片段。
     *
     * <p>返回值与 {@code current}/{@code size} 组合使用，避免不同筛选条件命中同一分页 key。
     * 片段由无符号十进制哈希和 {@code ':'} 组成，可安全拼接在 Redis key 前缀后。</p>
     */
    public static String listFilterSegment(Object... parts) {
        if (parts == null || parts.length == 0) {
            return "0:";
        }
        StringJoiner joiner = new StringJoiner("\u001f");
        for (Object part : parts) {
            joiner.add(part == null ? "\0" : String.valueOf(part));
        }
        return Integer.toUnsignedString(joiner.toString().hashCode()) + ":";
    }
}
