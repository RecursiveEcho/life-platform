package com.backend.lifeplatform.voucherOrders.exception;

/**
 * 表示消息重复投递导致的幂等冲突。
 *
 * <p>这不是需要重试的系统故障：数据库唯一索引已经保证已有订单，消费者可以直接确认消息。</p>
 */
public class DuplicateOrderMessageException extends RuntimeException {

    /** 保留底层唯一索引异常，便于日志定位并让事务回滚。 */
    public DuplicateOrderMessageException(Throwable cause) {
        super(cause);
    }
}
