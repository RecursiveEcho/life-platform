package com.backend.lifeplatform.voucher.constant;

/**
 * 秒杀活动生命周期状态常量。
 * <p>流转：DRAFT（草稿）→ PUBLISHING（发布中）→ PUBLISHED（已发布）；
 * 活动下架后置为 OFFLINE。PUBLISHING 表示发布流程已开始但尚未确认完成，
 * 用于让恢复任务识别中断的活动。</p>
 */
public final class SeckillActivityStatus {

    /** 草稿：已创建但尚未进入发布流程 */
    public static final String DRAFT = "DRAFT";
    /** 发布中：发布流程进行中，未确认完成（中断后由恢复任务接管） */
    public static final String PUBLISHING = "PUBLISHING";
    /** 已发布：活动已上线可抢购 */
    public static final String PUBLISHED = "PUBLISHED";
    /** 已下架 */
    public static final String OFFLINE = "OFFLINE";

    private SeckillActivityStatus() {
    }
}
