package com.jaguarliu.ai.session;

/**
 * Run 状态枚举
 */
public enum RunStatus {

    /**
     * 排队中，等待执行
     */
    QUEUED("queued"),

    /**
     * 执行中
     */
    RUNNING("running"),

    /**
     * 执行完成
     */
    DONE("done"),

    /**
     * 执行出错
     */
    ERROR("error"),

    /**
     * 已取消
     */
    CANCELED("canceled");

    private final String value;

    RunStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static RunStatus fromValue(String value) {
        for (RunStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status: " + value);
    }

    /**
     * 检查状态流转是否合法
     */
    public boolean canTransitionTo(RunStatus target) {
        return switch (this) {
            case QUEUED -> target == RUNNING || target == CANCELED;
            case RUNNING -> target == DONE || target == ERROR || target == CANCELED;
            case DONE, ERROR, CANCELED -> false; // 终态不能再流转
        };
    }
}
