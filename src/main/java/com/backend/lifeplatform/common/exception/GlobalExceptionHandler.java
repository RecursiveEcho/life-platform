package com.backend.lifeplatform.common.exception;

import com.backend.lifeplatform.common.enums.ErrorCode;
import com.backend.lifeplatform.common.result.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 将异常统一转换为 Result。 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 处理业务异常，按错误码把 HTTP 状态码映射为 400/404/429。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage(), e);
        HttpStatus status = e.getCode() == ErrorCode.RESOURCE_NOT_FOUND.getCode()
                ? HttpStatus.NOT_FOUND
                : e.getCode() == ErrorCode.RATE_LIMIT_EXCEEDED.getCode()
                ? HttpStatus.TOO_MANY_REQUESTS
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Result.fail(e.getCode(), e.getMessage()));
    }

    /** 处理 @RequestBody 请求体上的 @Valid 校验失败，返回第一个字段错误信息。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<?>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null ? "参数验证失败" : fieldError.getDefaultMessage();
        return ResponseEntity.badRequest()
                .body(Result.error(ErrorCode.PARAM_INVALID.getCode(), message));
    }

    /** 处理 @Validated 校验（方法参数、路径变量等）抛出的约束违例。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<?>> handleConstraintViolationException(
            ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("参数验证失败");
        return ResponseEntity.badRequest()
                .body(Result.error(ErrorCode.PARAM_INVALID.getCode(), message));
    }

    /** 处理请求体不可读：JSON 语法错误或字段类型与定义不匹配等。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<?>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(Result.error(ErrorCode.PARAM_INVALID.getCode(),
                        ErrorCode.PARAM_INVALID.getMessage()));
    }

    /** 处理上传文件大小超过服务器限制（默认 1MB）。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<?>> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Result.error(413, "上传文件超过服务器允许的大小"));
    }

    /** 处理请求了不存在的接口或静态资源，返回 404。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<?>> handleNoResourceFoundException(NoResourceFoundException e) {
        String message = "接口不存在: " + e.getHttpMethod().name() + " " + e.getResourcePath();
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(ErrorCode.API_NOT_FOUND.getCode(), message));
    }

    /** 处理请求方法不被支持，如对只允许 POST 的接口发起 GET。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<?>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException e) {
        String[] supported = e.getSupportedMethods();
        String supportedMethods = supported == null ? "未知" : String.join(", ", supported);
        String message = "请求方法 " + e.getMethod() + " 不被支持，支持: " + supportedMethods;
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.error(ErrorCode.METHOD_NOT_ALLOWED.getCode(), message));
    }

    /** 兜底处理器：捕获以上未覆盖的所有异常，避免把堆栈泄露给前端。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleAllUncaughtException(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(ErrorCode.SYSTEM_ERROR.getCode(),
                        ErrorCode.SYSTEM_ERROR.getMessage()));
    }
}
