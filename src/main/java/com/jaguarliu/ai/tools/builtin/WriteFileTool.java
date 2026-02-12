package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import com.jaguarliu.ai.tools.ToolsProperties;
import com.jaguarliu.ai.tools.WorkspaceResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 写入文件工具
 * 默认不需要 HITL 确认
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WriteFileTool implements Tool {

    private final ToolsProperties properties;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("write_file")
                .description("写入内容到指定文件。如果文件不存在则创建，存在则覆盖。路径相对于工作空间目录。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "path", Map.of(
                                        "type", "string",
                                        "description", "文件路径（相对于工作空间）"
                                ),
                                "content", Map.of(
                                        "type", "string",
                                        "description", "要写入的内容"
                                )
                        ),
                        "required", List.of("path", "content")
                ))
                .hitl(false)  // 默认不需要确认
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String pathStr = (String) arguments.get("path");
            String content = (String) arguments.get("content");

            if (pathStr == null || pathStr.isBlank()) {
                return ToolResult.error("Missing required parameter: path");
            }
            if (content == null) {
                return ToolResult.error("Missing required parameter: content");
            }

            try {
                Path workspacePath = WorkspaceResolver.resolveSessionWorkspace(properties);
                Path filePath = workspacePath.resolve(pathStr).normalize();

                // 安全检查：写入只允许在 workspace 内（skill 资源目录只允许读取，不允许写入）
                if (!filePath.startsWith(workspacePath)) {
                    log.warn("Write path traversal attempt: {}", pathStr);
                    return ToolResult.error("Access denied: write only allowed within workspace");
                }

                // 检查内容大小
                if (content.length() > properties.getMaxFileSize()) {
                    return ToolResult.error("Content too large: " + content.length() + " bytes (max: " + properties.getMaxFileSize() + ")");
                }

                // 确保父目录存在
                Path parentDir = filePath.getParent();
                if (parentDir != null && !Files.exists(parentDir)) {
                    Files.createDirectories(parentDir);
                }

                // 写入文件
                Files.writeString(filePath, content, StandardCharsets.UTF_8);
                log.info("Wrote file: {} ({} bytes)", pathStr, content.length());

                return ToolResult.success("Successfully wrote " + content.length() + " bytes to " + pathStr);

            } catch (IOException e) {
                log.error("Failed to write file: {}", pathStr, e);
                return ToolResult.error("Failed to write file: " + e.getMessage());
            }
        });
    }
}
