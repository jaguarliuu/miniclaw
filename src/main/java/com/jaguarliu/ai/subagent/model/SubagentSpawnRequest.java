package com.jaguarliu.ai.subagent.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * SubAgent spawn 请求参数
 */
@Data
@Builder
public class SubagentSpawnRequest {

    /**
     * 任务描述/提示
     */
    private String task;

    /**
     * 目标 Agent Profile ID（可选，默认继承父）
     */
    private String agentId;

    /**
     * 是否转发中间流到父会话
     */
    @Builder.Default
    private boolean deliver = false;

    /**
     * 是否在完成后回传结果到父会话
     */
    @Builder.Default
    private boolean announce = true;

    /**
     * 超时时间（秒）
     */
    @Builder.Default
    private int timeoutSeconds = 600;

    /**
     * 自定义元数据
     */
    private Map<String, Object> metadata;
}
