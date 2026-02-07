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
public class SendEmailTool implements Tool {

    private final ChannelService channelService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("send_email")
                .description("通过邮箱渠道发送邮件。channel 参数可以传渠道名称或直接传 'email'（自动使用第一个启用的邮箱渠道）。需要先在 /channels 中配置 Email 渠道。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "channel", Map.of(
                                        "type", "string",
                                        "description", "渠道名称或类型。传 'email' 自动使用第一个启用的邮箱渠道，也可传具体渠道名称"
                                ),
                                "to", Map.of(
                                        "type", "string",
                                        "description", "收件人地址（逗号分隔多个）"
                                ),
                                "subject", Map.of(
                                        "type", "string",
                                        "description", "邮件主题"
                                ),
                                "body", Map.of(
                                        "type", "string",
                                        "description", "邮件正文，支持 HTML 格式（如 <h1>、<p>、<table> 等）。纯文本也可以，换行会自动转为 <br>"
                                ),
                                "cc", Map.of(
                                        "type", "string",
                                        "description", "抄送地址（逗号分隔多个，可选）"
                                )
                        ),
                        "required", List.of("channel", "to", "subject", "body")
                ))
                .hitl(true)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String channel = (String) arguments.get("channel");
            String to = (String) arguments.get("to");
            String subject = (String) arguments.get("subject");
            String body = (String) arguments.get("body");
            String cc = (String) arguments.get("cc");

            if (channel == null || channel.isBlank()) {
                return ToolResult.error("Missing required parameter: channel");
            }
            if (to == null || to.isBlank()) {
                return ToolResult.error("Missing required parameter: to");
            }
            if (subject == null || subject.isBlank()) {
                return ToolResult.error("Missing required parameter: subject");
            }
            if (body == null || body.isBlank()) {
                return ToolResult.error("Missing required parameter: body");
            }

            String result = channelService.sendEmail(channel, to, subject, body, cc);
            return ToolResult.success(result);
        }).onErrorResume(e -> {
            log.error("send_email failed: {}", e.getMessage());
            return Mono.just(ToolResult.error(e.getMessage()));
        });
    }
}
