package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.channel.ChannelService;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SendWebhookTool implements Tool {

    private final ChannelService channelService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("send_webhook")
                .description("通过 Webhook 渠道发送请求。channel 参数可以传渠道名称或直接传 'webhook'（自动使用第一个启用的 Webhook 渠道）。需要先在 /channels 中配置 Webhook 渠道。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "channel", Map.of(
                                        "type", "string",
                                        "description", "渠道名称或类型。传 'webhook' 自动使用第一个启用的 Webhook 渠道，也可传具体渠道名称"
                                ),
                                "payload", Map.of(
                                        "type", "string",
                                        "description", "请求体（JSON 字符串）"
                                )
                        ),
                        "required", List.of("channel", "payload")
                ))
                .hitl(true)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String channel = (String) arguments.get("channel");
            String payload = (String) arguments.get("payload");

            if (channel == null || channel.isBlank()) {
                return ToolResult.error("Missing required parameter: channel");
            }
            if (payload == null || payload.isBlank()) {
                return ToolResult.error("Missing required parameter: payload");
            }

            String result = channelService.sendWebhook(channel, payload);
            return ToolResult.success(result);
        }).onErrorResume(e -> {
            log.error("send_webhook failed: {}", e.getMessage());
            return Mono.just(ToolResult.error(e.getMessage()));
        });
    }
}
