package com.miniclaw.gateway.rpc;

/**
 * Gateway 方法的调用模式。
 * UNARY 表示一次请求返回一次结果，
 * STREAMING 表示一次请求会持续回推事件。
 */
public enum GatewayInvocationMode {
    UNARY,
    STREAMING
}
