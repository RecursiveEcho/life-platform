package com.backend.lifeplatform.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用分页结果。
 *
 * @param <T> 当前页数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 当前页数据 */
    private List<T> records;

    /** 符合条件的总记录数 */
    private long total;

    /** 当前页码，从 1 开始 */
    private long current;

    /** 每页记录数 */
    private long size;

    /** 总页数 */
    private long pages;

    /**
     * 根据总记录数和每页大小构造分页结果。
     */
    public static <T> PageResult<T> of(
            List<T> records,
            long total,
            long current,
            long size
    ) {
        long pages = size <= 0 ? 0 : (total + size - 1) / size;
        return new PageResult<>(records, total, current, size, pages);
    }
}
