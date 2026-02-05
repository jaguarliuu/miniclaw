package com.jaguarliu.ai.memory.store;

import com.jaguarliu.ai.memory.MemoryProperties;
import com.jaguarliu.ai.tools.ToolsProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

/**
 * 全局记忆文件存储
 *
 * 设计原则：
 * - 记忆是全局的、跨会话的（个人助手，非多租户）
 * - Markdown 是真相源（source of truth）
 * - 写入 = 纯文件操作，不触发 embedding
 * - 索引更新由 MemoryIndexer 异步/按需完成
 *
 * 存储结构：
 * workspace/memory/
 *   MEMORY.md           - 核心长期记忆（全局）
 *   2026-01-15.md       - 日记式追加（全局）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryStore {

    private final MemoryProperties memoryProperties;
    private final ToolsProperties toolsProperties;

    private Path memoryDir;

    @PostConstruct
    public void init() {
        memoryDir = Path.of(toolsProperties.getWorkspace())
                .resolve(memoryProperties.getPath())
                .toAbsolutePath()
                .normalize();

        try {
            Files.createDirectories(memoryDir);
            log.info("Global memory store initialized: {}", memoryDir);
        } catch (IOException e) {
            log.error("Failed to create memory directory: {}", memoryDir, e);
        }
    }

    /**
     * 获取记忆目录路径
     */
    public Path getMemoryDir() {
        return memoryDir;
    }

    /**
     * 追加内容到核心记忆 MEMORY.md（全局长期记忆）
     */
    public void appendToCore(String content) throws IOException {
        Path corePath = memoryDir.resolve("MEMORY.md");
        appendToFile(corePath, content);
        log.info("Appended to global MEMORY.md: {} chars", content.length());
    }

    /**
     * 追加内容到今天的日记文件（全局日记）
     */
    public void appendToDaily(String content) throws IOException {
        String fileName = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".md";
        Path dailyPath = memoryDir.resolve(fileName);
        appendToFile(dailyPath, content);
        log.info("Appended to global daily log {}: {} chars", fileName, content.length());
    }

    /**
     * 追加内容到指定文件
     */
    public void appendToFile(Path filePath, String content) throws IOException {
        validatePath(filePath);

        // 确保父目录存在
        Files.createDirectories(filePath.getParent());

        // 如果文件已存在且不为空，加一个空行分隔
        if (Files.exists(filePath) && Files.size(filePath) > 0) {
            content = "\n" + content;
        }

        Files.writeString(filePath, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * 读取指定记忆文件
     *
     * @param relativePath 相对于 memory 目录的路径
     * @return 文件内容
     */
    public String read(String relativePath) throws IOException {
        Path filePath = memoryDir.resolve(relativePath).normalize();
        validatePath(filePath);

        if (!Files.exists(filePath)) {
            throw new IOException("Memory file not found: " + relativePath);
        }

        return Files.readString(filePath, StandardCharsets.UTF_8);
    }

    /**
     * 读取指定行范围
     *
     * @param relativePath 相对路径
     * @param startLine    起始行（1-based）
     * @param limit        读取行数
     * @return 指定范围的内容
     */
    public String readLines(String relativePath, int startLine, int limit) throws IOException {
        Path filePath = memoryDir.resolve(relativePath).normalize();
        validatePath(filePath);

        if (!Files.exists(filePath)) {
            throw new IOException("Memory file not found: " + relativePath);
        }

        List<String> allLines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        int start = Math.max(0, startLine - 1); // 转为 0-based
        int end = Math.min(allLines.size(), start + limit);

        if (start >= allLines.size()) {
            return "";
        }

        return String.join("\n", allLines.subList(start, end));
    }

    /**
     * 列出所有记忆文件（全局）
     */
    public List<MemoryFileInfo> listFiles() throws IOException {
        if (!Files.exists(memoryDir)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.walk(memoryDir, 1)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md"))
                    .map(p -> {
                        try {
                            return new MemoryFileInfo(
                                    memoryDir.relativize(p).toString().replace('\\', '/'),
                                    Files.size(p),
                                    Files.getLastModifiedTime(p).toMillis()
                            );
                        } catch (IOException e) {
                            return null;
                        }
                    })
                    .filter(java.util.Objects::nonNull)
                    .sorted((a, b) -> b.relativePath().compareTo(a.relativePath()))
                    .toList();
        }
    }

    /**
     * 检查核心记忆文件是否存在
     */
    public boolean coreMemoryExists() {
        return Files.exists(memoryDir.resolve("MEMORY.md"));
    }

    /**
     * 路径安全校验
     */
    private void validatePath(Path filePath) throws IOException {
        Path normalized = filePath.toAbsolutePath().normalize();
        if (!normalized.startsWith(memoryDir)) {
            throw new IOException("Access denied: path outside memory directory");
        }
    }

    /**
     * 记忆文件信息
     */
    public record MemoryFileInfo(String relativePath, long sizeBytes, long lastModifiedMs) {}
}
