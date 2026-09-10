package com.backend.lifeplatform.common.enums;

/** 统一业务错误码。 */
public enum ErrorCode {

    // ===== 用户 / 认证 (1xxx) =====
    USER_NOT_FOUND(1001, "用户不存在"),
    LOGIN_FAILED(1002, "手机号或密码错误"),
    PHONE_ALREADY_REGISTERED(1003, "手机号已注册"),
    INVALID_TOKEN(1004, "无效的令牌"),
    TOKEN_EXPIRED(1005, "令牌已过期"),
    SMS_CODE_INVALID(1006, "短信验证码错误"),
    SMS_CODE_EXPIRED(1007, "短信验证码已过期"),
    SMS_SEND_TOO_FREQUENT(1008, "短信发送过于频繁"),
    OLD_PASSWORD_ERROR(1009, "原密码错误"),
    FORBIDDEN(1010, "没有操作权限"),
    ACCOUNT_DISABLED(1021, "账号已停用"),

    // ===== 商家 / 店铺 (2xxx) =====
    SHOP_NOT_FOUND(2001, "店铺不存在"),
    SHOP_CLOSED(2002, "店铺暂未营业"),
    MERCHANT_APPLICATION_EXISTS(2003, "已有入驻申请正在审核中"),
    MERCHANT_APPLICATION_NOT_FOUND(2004, "入驻申请不存在"),
    MERCHANT_APPLICATION_STATUS_ERROR(2005, "入驻申请状态不允许该操作"),
    SHOP_ALREADY_FOLLOWED(2006, "已关注该店铺"),
    SHOP_NOT_FOLLOWED(2007, "尚未关注该店铺"),
    SHOP_ALREADY_FAVORITED(2008, "已收藏该店铺"),
    SHOP_NOT_FAVORITED(2009, "尚未收藏该店铺"),
    REVIEW_ALREADY_SUBMITTED(2011, "您已评价过该店铺"),
    REVIEW_NOT_FOUND(2012, "评价不存在"),

    // ===== 优惠券 / 秒杀 (3xxx) =====
    VOUCHER_NOT_FOUND(3001, "优惠券不存在"),
    VOUCHER_SOLD_OUT(3002, "优惠券已抢光"),
    VOUCHER_STOCK_NOT_ENOUGH(3003, "优惠券库存不足"),
    VOUCHER_EXPIRED(3004, "优惠券已过期"),
    VOUCHER_NOT_AVAILABLE(3005, "优惠券暂不可用"),
    VOUCHER_ALREADY_GRABBED(3006, "您已抢过该优惠券"),
    VOUCHER_PER_USER_LIMIT(3007, "超出每人限购数量"),
    SECKILL_NOT_STARTED(3011, "秒杀活动尚未开始"),
    SECKILL_ENDED(3012, "秒杀活动已结束"),
    SECKILL_OUT_OF_TIME(3013, "不在秒杀时间范围内"),

    // ===== 通用业务 (4xxx) =====
    INVALID_OPERATION(4001, "非法操作"),
    RESOURCE_NOT_FOUND(4003, "资源不存在"),
    RESOURCE_EXISTS(4004, "资源已存在"),
    PARAM_INVALID(4011, "请求参数无效"),
    PARAM_MISSING(4012, "缺少必要参数"),
    PARAMS_ERROR(4013, "请求参数错误"),
    RATE_LIMIT_EXCEEDED(4041, "请求过于频繁，请稍后重试"),
    LOCATION_FAILED(4051, "定位失败"),
    AREA_NOT_SERVED(4052, "当前区域暂不支持配送"),

    // ===== 订单 / 支付 (42xx) =====
    ORDER_NOT_FOUND(4201, "订单不存在"),
    ORDER_STATUS_ERROR(4202, "当前订单状态不允许该操作"),
    ORDER_CREATE_FAILED(4203, "下单失败，请稍后重试"),
    ORDER_CANCEL_NOT_ALLOWED(4204, "当前状态无法取消订单"),
    PAYMENT_FAILED(4211, "支付失败"),
    PAYMENT_TIMEOUT(4212, "支付超时"),
    BALANCE_NOT_ENOUGH(4213, "余额不足"),
    REFUND_NOT_ALLOWED(4221, "当前状态不支持退款"),
    REFUND_PROCESSING(4222, "退款正在处理中"),

    // ===== 积分 / 会员 (43xx) =====
    POINTS_NOT_ENOUGH(4301, "积分不足"),
    SIGNED_TODAY(4302, "今日已签到"),
    MEMBER_LEVEL_NOT_ENOUGH(4303, "会员等级不足"),

    // ===== 系统 (5xxx) =====
    SYSTEM_ERROR(5001, "系统内部错误"),
    DB_ERROR(5002, "数据库操作失败"),
    CACHE_ERROR(5005, "缓存操作失败"),
    SYSTEM_BUSY(5011, "系统繁忙，请稍后重试"),
    TIMEOUT_ERROR(5013, "请求超时"),
    FILE_UPLOAD_FAILED(5015, "文件上传失败"),
    FILE_SIZE_EXCEEDED(5016, "文件大小超出限制"),
    FILE_TYPE_NOT_SUPPORTED(5017, "不支持的文件类型"),

    // ===== 接口 (6xxx) =====
    API_NOT_FOUND(6001, "接口不存在"),
    METHOD_NOT_ALLOWED(6002, "请求方法不被支持"),
    MISSING_AUTH_HEADER(6003, "缺少认证信息");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
