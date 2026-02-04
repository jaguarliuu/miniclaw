package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolExecutionContext;
import com.jaguarliu.ai.tools.ToolResult;
import com.jaguarliu.ai.tools.ToolsProperties;
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
 * 读取文件工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadFileTool implements Tool {

    private final ToolsProperties properties;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("read_file")
                .description("读取指定文件的内容。路径可以是相对于工作空间的路径，也可以是绝对路径（如 skill 资源目录）。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "path", Map.of(
                                        "type", "string",
                                        "description", "文件路径（相对于工作空间，或绝对路径）"
                                )
                        ),
                        "required", List.of("path")
                ))
                .hitl(false)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String pathStr = (String) arguments.get("path");
            if (pathStr == null || pathStr.isBlank()) {
                return ToolResult.error("Missing required parameter: path");
            }

            try {
                Path filePath = resolvePath(pathStr);
                if (filePath == null) {
                    log.warn("Path access denied: {}", pathStr);
                    return ToolResult.error("Access denied: path outside workspace");
                }

                if (!Files.exists(filePath)) {
                    return ToolResult.error("File not found: " + pathStr);
                }

                if (!Files.isRegularFile(filePath)) {
                    return ToolResult.error("Not a file: " + pathStr);
                }

                // 检查文件大小
                long size = Files.size(filePath);
                if (size > properties.getMaxFileSize()) {
                    return ToolResult.error("File too large: " + size + " bytes (max: " + properties.getMaxFileSize() + ")");
                }

                String content = Files.readString(filePath, StandardCharsets.UTF_8);
                log.info("Read file: {} ({} bytes)", pathStr, content.length());

                return ToolResult.success(content);

            } catch (IOException e) {
                log.error("Failed to read file: {}", pathStr, e);
                return ToolResult.error("Failed to read file: " + e.getMessage());
            }
        });
    }

    /**
     * 解析并验证文件路径
     * 优先级：
     * 1. workspace 内的相对路径
     * 2. 绝对路径在 workspace 内
     * 3. 绝对路径在 ToolExecutionContext 额外允许的路径内（如 skill 资源目录）
     *
     * @return 解析后的安全路径，或 null 表示拒绝访问
     */
    private Path resolvePath(String pathStr) {
        Path workspacePath = Path.of(properties.getWorkspace()).toAbsolutePath().normalize();

        // 先尝试作为 workspace 内的路径解析
        Path filePath = workspacePath.resolve(pathStr).normalize();
        if (filePath.startsWith(workspacePath)) {
            return filePath;
        }

        // 不在 workspace 内，检查是否为允许的额外路径（如 skill 资源目录）
        ToolExecutionContext ctx = ToolExecutionContext.current();
        if (ctx != null) {
            // 尝试绝对路径
            Path absolutePath = Path.of(pathStr).toAbsolutePath().normalize();
            if (ctx.isPathAllowed(absolutePath)) {
                return absolutePath;
            }
        }

        return null;
    }
}
