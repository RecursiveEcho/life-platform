package com.backend.lifeplatform.common.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link CacheKeyUtils} 的单元测试，覆盖 key 拼接与筛选片段生成的规则。 */
class CacheKeyUtilsTest {

    @Test
    void detailKey_appendsIdentifierToPrefix() {
        assertThat(CacheKeyUtils.detailKey("cache:shop:detail:", 42L))
                .isEqualTo("cache:shop:detail:42");
    }

    @Test
    void listFilterSegment_whenNoFilters_returnsStableDefaultSegment() {
        assertThat(CacheKeyUtils.listFilterSegment()).isEqualTo("0:");
        assertThat(CacheKeyUtils.listFilterSegment((Object[]) null)).isEqualTo("0:");
    }

    @Test
    void listFilterSegment_usesOrderAndNullAsPartOfTheFilterDimensions() {
        String expected = Integer.toUnsignedString("type\u001fcoffee".hashCode()) + ":";

        assertThat(CacheKeyUtils.listFilterSegment("type", "coffee"))
                .isEqualTo(expected);
        assertThat(CacheKeyUtils.listFilterSegment("coffee", "type"))
                .isNotEqualTo(expected);
        assertThat(CacheKeyUtils.listFilterSegment("type", null))
                .isNotEqualTo(CacheKeyUtils.listFilterSegment("type"));
    }
}
