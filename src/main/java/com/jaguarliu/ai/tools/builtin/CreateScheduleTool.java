package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.channel.ChannelEntity;
import com.jaguarliu.ai.channel.ChannelRepository;
import com.jaguarliu.ai.schedule.ScheduledTaskService;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * create_schedule 工具
 * 让 Agent 通过自然语言创建定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateScheduleTool implements Tool {

    private final ScheduledTaskService scheduledTaskService;
    private final ChannelRepository channelRepository;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("create_schedule")
                .description("创建定时执行任务。指定名称、cron 表达式、要执行的 prompt、推送渠道和收件人。" +
                        "创建后系统会按 cron 表达式定时执行 prompt 并将结果推送到指定渠道。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "name", Map.of(
                                        "type", "string",
                                        "description", "任务名称，简短描述任务用途"
                                ),
                                "cron", Map.of(
                                        "type", "string",
                                        "description", "cron 表达式（5 段格式），如 \"0 9 * * *\" 表示每天9点，\"0 * * * *\" 表示每小时"
                                ),
                                "prompt", Map.of(
                                        "type", "string",
                                        "description", "触发时让 Agent 执行的 prompt 指令"
                                ),
                                "channel", Map.of(
                                        "type", "string",
                                        "description", "渠道名称或类型（email/webhook），用于推送执行结果"
                                ),
                                "email_to", Map.of(
                                        "type", "string",
                                        "description", "邮箱渠道的收件人地址（仅 email 渠道需要）"
                                ),
                                "email_cc", Map.of(
                                        "type", "string",
                                        "description", "邮箱渠道的抄送地址（可选）"
                                )
                        ),
                        "required", List.of("name", "cron", "prompt", "channel")
                ))
                .hitl(true)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        String name = (String) arguments.get("name");
        String cron = (String) arguments.get("cron");
        String prompt = (String) arguments.get("prompt");
        String channel = (String) arguments.get("channel");
        String emailTo = (String) arguments.get("email_to");
        String emailCc = (String) arguments.get("email_cc");

        if (name == null || name.isBlank()) {
            return Mono.just(ToolResult.error("Missing required parameter: name"));
        }
        if (cron == null || cron.isBlank()) {
            return Mono.just(ToolResult.error("Missing required parameter: cron"));
        }
        if (prompt == null || prompt.isBlank()) {
            return Mono.just(ToolResult.error("Missing required parameter: prompt"));
        }
        if (channel == null || channel.isBlank()) {
            return Mono.just(ToolResult.error("Missing required parameter: channel"));
        }

        try {
            // 解析渠道：先按 name 查，再按 type 查
            ChannelEntity channelEntity = resolveChannel(channel);

            // email 渠道需要收件人
            if ("email".equals(channelEntity.getType()) && (emailTo == null || emailTo.isBlank())) {
                return Mono.just(ToolResult.error("email_to is required for email channels"));
            }

            var task = scheduledTaskService.create(
                    name, cron, prompt,
                    channelEntity.getId(), channelEntity.getType(),
                    emailTo, emailCc);

            log.info("Schedule created via tool: name={}, cron={}", name, cron);
            return Mono.just(ToolResult.success(
                    "定时任务已创建：\n" +
                    "- 名称: " + task.getName() + "\n" +
                    "- Cron: " + task.getCronExpr() + "\n" +
                    "- 渠道: " + channelEntity.getName() + " (" + channelEntity.getType() + ")\n" +
                    "- 状态: 已启用"));

        } catch (Exception e) {
            log.error("Failed to create schedule via tool: {}", e.getMessage());
            return Mono.just(ToolResult.error("创建定时任务失败: " + e.getMessage()));
        }
    }

    private ChannelEntity resolveChannel(String nameOrType) {
        // 1. 按名称精确匹配
        Optional<ChannelEntity> byName = channelRepository.findByName(nameOrType);
        if (byName.isPresent()) {
            return byName.get();
        }

        // 2. 按类型匹配第一个启用的
        List<ChannelEntity> byType = channelRepository.findByEnabledTrueAndType(nameOrType);
        if (!byType.isEmpty()) {
            return byType.get(0);
        }

        throw new IllegalArgumentException("Channel not found: '" + nameOrType +
                "'. Please create a channel in /channels first.");
    }
}
