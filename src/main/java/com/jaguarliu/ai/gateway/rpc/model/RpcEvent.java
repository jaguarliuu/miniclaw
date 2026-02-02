package com.jaguarliu.ai.gateway.rpc.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * RPC 事件消息（服务端主动推送）
 * 格式: {"type":"event","event":"xxx","runId":"xxx","payload":{...}}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RpcEvent {

    /**
     * 消息类型，固定为 "event"
     */
    private String type;

    /**
     * 事件名称，如 "lifecycle.start", "assistant.delta"
     */
    private String event;

    /**
     * 关联的 runId
     */
    private String runId;

    /**
     * 事件数据
     */
    private Object payload;

    /**
     * 创建事件
     */
    public static RpcEvent of(String event, String runId, Object payload) {
        return RpcEvent.builder()
                .type("event")
                .event(event)
                .runId(runId)
                .payload(payload)
                .build();
    }
}
