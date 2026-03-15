package com.miniclaw.llm;

public class LlmException extends RuntimeException {

    private final LlmErrorType errorType;
    private final boolean retryable;
    private final Integer httpStatus;

    public LlmException(LlmErrorType errorType, boolean retryable, Integer httpStatus, String message) {
        super(message);
        this.errorType = errorType;
        this.retryable = retryable;
        this.httpStatus = httpStatus;
    }

    public LlmException(LlmErrorType errorType, boolean retryable, Integer httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.retryable = retryable;
        this.httpStatus = httpStatus;
    }

    public LlmErrorType getErrorType() {
        return errorType;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }
}
