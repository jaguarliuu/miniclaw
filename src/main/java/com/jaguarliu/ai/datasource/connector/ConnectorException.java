package com.jaguarliu.ai.datasource.connector;

/**
 * 连接器异常
 *
 * 封装数据源连接和操作过程中的所有异常
 */
public class ConnectorException extends Exception {

    /** 异常类型 */
    private final ErrorType errorType;

    public ConnectorException(String message) {
        this(message, ErrorType.UNKNOWN);
    }

    public ConnectorException(String message, ErrorType errorType) {
        super(message);
        this.errorType = errorType;
    }

    public ConnectorException(String message, Throwable cause) {
        this(message, cause, ErrorType.UNKNOWN);
    }

    public ConnectorException(String message, Throwable cause, ErrorType errorType) {
        super(message, cause);
        this.errorType = errorType;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    /**
     * 异常类型枚举
     */
    public enum ErrorType {
        /** 连接失败 */
        CONNECTION_FAILED,

        /** 认证失败 */
        AUTHENTICATION_FAILED,

        /** 超时 */
        TIMEOUT,

        /** SQL 语法错误 */
        SQL_SYNTAX_ERROR,

        /** 权限不足 */
        PERMISSION_DENIED,

        /** 资源不存在（表、文件等） */
        RESOURCE_NOT_FOUND,

        /** 文件格式错误 */
        FILE_FORMAT_ERROR,

        /** 结果集过大 */
        RESULT_TOO_LARGE,

        /** 配置错误 */
        CONFIGURATION_ERROR,

        /** 未知错误 */
        UNKNOWN
    }

    /**
     * 判断是否为可重试的错误
     */
    public boolean isRetryable() {
        return errorType == ErrorType.TIMEOUT
                || errorType == ErrorType.CONNECTION_FAILED;
    }

    /**
     * 判断是否为配置相关错误
     */
    public boolean isConfigurationRelated() {
        return errorType == ErrorType.CONFIGURATION_ERROR
                || errorType == ErrorType.AUTHENTICATION_FAILED
                || errorType == ErrorType.PERMISSION_DENIED;
    }
}
