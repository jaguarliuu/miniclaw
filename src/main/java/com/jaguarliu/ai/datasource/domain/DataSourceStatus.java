package com.jaguarliu.ai.datasource.domain;

/**
 * 数据源状态
 */
public enum DataSourceStatus {
    /** 活跃状态，可正常使用 */
    ACTIVE,

    /** 未激活，需要先测试连接 */
    INACTIVE,

    /** 连接错误 */
    ERROR,

    /** 已禁用 */
    DISABLED
}
