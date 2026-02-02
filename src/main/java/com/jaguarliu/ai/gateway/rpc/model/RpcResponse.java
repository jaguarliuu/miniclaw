package com.jaguarliu.ai.gateway.rpc.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * RPC 响应消息
 * 格式: {"type":"response","id":"xxx","payload":{...}} 或 {"type":"response","id":"xxx","error":{...}}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RpcResponse {

    /**
     * 消息类型，固定为 "response"
     */
    private String type;

    /**
     * 请求 ID，与请求对应
     */
    private String id;

    /**
     * 响应数据（成功时）
     */
    private Object payload;

    /**
     * 错误信息（失败时）
     */
    private RpcError error;

    /**
     * 创建成功响应
     */
    public static RpcResponse success(String id, Object payload) {
        return RpcResponse.builder()
                .type("response")
                .id(id)
                .payload(payload)
                .build();
    }

    /**
     * 创建错误响应
     */
    public static RpcResponse error(String id, String code, String message) {
        return RpcResponse.builder()
                .type("response")
                .id(id)
                .error(new RpcError(code, message))
                .build();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RpcError {
        private String code;
        private String message;
    }
}
