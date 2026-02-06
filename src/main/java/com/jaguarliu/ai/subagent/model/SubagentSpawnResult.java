package com.jaguarliu.ai.subagent.model;

import lombok.Builder;
import lombok.Data;

/**
 * SubAgent spawn 结果
 */
@Data
@Builder
public class SubagentSpawnResult {

    /**
     * 是否接受
     */
    private boolean accepted;

    /**
     * 子会话 ID
     */
    private String subSessionId;

    /**
     * 子运行 ID
     */
    private String subRunId;

    /**
     * 会话 Key（用于标识和查询）
     */
    private String sessionKey;

    /**
     * 执行通道
     */
    private String lane;

    /**
     * 错误信息（如果 accepted=false）
     */
    private String error;

    /**
     * 创建成功结果
     */
    public static SubagentSpawnResult success(String subSessionId, String subRunId, String sessionKey) {
        return SubagentSpawnResult.builder()
                .accepted(true)
                .subSessionId(subSessionId)
                .subRunId(subRunId)
                .sessionKey(sessionKey)
                .lane("subagent")
                .build();
    }

    /**
     * 创建失败结果
     */
    public static SubagentSpawnResult failure(String error) {
        return SubagentSpawnResult.builder()
                .accepted(false)
                .error(error)
                .build();
    }
}
