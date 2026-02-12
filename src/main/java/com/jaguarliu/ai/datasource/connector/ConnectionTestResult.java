package com.jaguarliu.ai.datasource.connector;

import lombok.Builder;
import lombok.Getter;

/**
 * 连接测试结果
 * 值对象
 */
@Getter
@Builder
public class ConnectionTestResult {

    /** 是否成功 */
    private boolean success;

    /** 响应时间（毫秒） */
    private long responseTime;

    /** 错误信息（如果失败） */
    private String errorMessage;

    /** 详细信息 */
    private String details;

    /**
     * 创建成功的测试结果
     */
    public static ConnectionTestResult success(long responseTime, String details) {
        return ConnectionTestResult.builder()
                .success(true)
                .responseTime(responseTime)
                .details(details)
                .build();
    }

    /**
     * 创建失败的测试结果
     */
    public static ConnectionTestResult failure(String errorMessage) {
        return ConnectionTestResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 创建失败的测试结果（带响应时间）
     */
    public static ConnectionTestResult failure(long responseTime, String errorMessage) {
        return ConnectionTestResult.builder()
                .success(false)
                .responseTime(responseTime)
                .errorMessage(errorMessage)
                .build();
    }
}
