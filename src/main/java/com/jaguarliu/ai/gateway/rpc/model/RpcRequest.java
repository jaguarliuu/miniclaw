package com.jaguarliu.ai.gateway.rpc.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * RPC 请求消息
 * 格式: {"type":"request","id":"xxx","method":"xxx","payload":{...}}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RpcRequest {

    /**
     * 消息类型，固定为 "request"
     */
    private String type;

    /**
     * 请求 ID，用于关联响应
     */
    private String id;

    /**
     * 方法名，如 "agent.run", "session.create"
     */
    private String method;

    /**
     * 请求参数
     */
    private Object payload;
}
