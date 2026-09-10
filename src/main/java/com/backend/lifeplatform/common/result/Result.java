package com.backend.lifeplatform.common.result;

/**
 * 统一的接口响应包装类。
 * 所有 Controller 接口都返回该类型，前端通过 code 判断成功/失败，data 承载业务数据。
 *
 * @param <T> 业务数据类型
 */
public class Result<T> {

    /** 成功状态码 */
    public static final Integer SUCCESS_CODE = 200;
    /** 成功提示信息 */
    public static final String SUCCESS_MESSAGE = "success";

    /** 状态码，200 表示成功 */
    private Integer code;
    /** 提示信息，成功为 success，失败为具体错误描述 */
    private String message;
    /** 业务数据，失败时为 null */
    private T data;

    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功响应，无数据 */
    public static <T> Result<T> success() {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, null);
    }

    /** 成功响应，携带业务数据 */
    public static <T> Result<T> success(T data) {
        return new Result<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    /** 失败响应，携带错误码和错误信息 */
    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    /** 失败响应（与 {@link #fail} 语义等价，供错误场景命名更直白时使用）。 */
    public static <T> Result<T> error(Integer code, String message) {
        return fail(code, message);
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}
