package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Ping 工具（测试用）
 * 用于验证工具系统是否正常工作
 */
@Component
public class PingTool implements Tool {

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("ping")
                .description("测试工具，返回 pong。用于验证工具系统是否正常。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "message", Map.of(
                                        "type", "string",
                                        "description", "可选的消息，会在响应中回显"
                                )
                        ),
                        "required", java.util.List.of()
                ))
                .hitl(false)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        String message = (String) arguments.getOrDefault("message", "");
        String response = message.isEmpty() ? "pong" : "pong: " + message;
        return Mono.just(ToolResult.success(response));
    }
}
