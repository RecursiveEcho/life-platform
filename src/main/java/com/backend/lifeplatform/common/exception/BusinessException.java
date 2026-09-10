package com.backend.lifeplatform.common.exception;

import com.backend.lifeplatform.common.enums.ErrorCode;

/** 携带业务错误码的异常。 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String customMessage;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.customMessage = null;
    }

    public BusinessException(String customMessage) {
        super(customMessage);
        this.errorCode = ErrorCode.INVALID_OPERATION;
        this.customMessage = customMessage;
    }

    public BusinessException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
        this.customMessage = customMessage;
    }

    public int getCode() {
        return errorCode.getCode();
    }

    @Override
    public String getMessage() {
        return customMessage == null ? errorCode.getMessage() : customMessage;
    }
}
