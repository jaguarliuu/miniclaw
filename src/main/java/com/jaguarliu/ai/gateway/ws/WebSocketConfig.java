package com.jaguarliu.ai.gateway.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.Map;

/**
 * WebSocket 配置类
 */
@Configuration
@RequiredArgsConstructor
public class WebSocketConfig {

    private final GatewayWebSocketHandler gatewayWebSocketHandler;

    /**
     * WebSocket 路由映射
     */
    @Bean
    public HandlerMapping webSocketHandlerMapping() {
        Map<String, Object> urlMap = Map.of("/ws", gatewayWebSocketHandler);
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(urlMap);
        mapping.setOrder(1);
        return mapping;
    }

    /**
     * WebSocket 处理器适配器
     */
    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
