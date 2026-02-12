package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolExecutionContext;
import com.jaguarliu.ai.tools.ToolResult;
import com.jaguarliu.ai.tools.ToolsProperties;
import com.jaguarliu.ai.tools.WorkspaceResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 读取文件工具 — 支持文本文件和二进制文档（PDF/DOCX/PPTX/XLSX）。
 * <p>
 * 文本文件直接以 UTF-8 读取；二进制文档通过 Apache Tika / POI 提取文本。
 * XLSX 使用 POI 逐单元格提取以保留表格结构。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadFileTool implements Tool {

    private final ToolsProperties properties;

    /** 通过 Tika 解析的 MIME 类型 */
    private static final Set<String> TIKA_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    /** 通过 POI 解析的 MIME 类型 */
    private static final Set<String> XLSX_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    /** 按扩展名映射 MIME（Tika 检测可能不准确时的兜底） */
    private static final Map<String, String> EXT_MIME = Map.of(
            ".pdf", "application/pdf",
            ".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            ".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            ".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("read_file")
                .description("读取指定文件的内容。支持文本文件（txt/md/json/yaml 等）和二进制文档（PDF/DOCX/XLSX/PPTX）。" +
                        "二进制文档会自动提取文本内容。路径可以是相对于工作空间的路径，也可以是绝对路径。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "path", Map.of(
                                        "type", "string",
                                        "description", "文件路径（相对于工作空间，或绝对路径）"
                                ),
                                "offset", Map.of(
                                        "type", "integer",
                                        "description", "起始字符偏移量（用于分段读取大文件），默认 0"
                                ),
                                "limit", Map.of(
                                        "type", "integer",
                                        "description", "最多读取的字符数（用于分段读取大文件），默认读取全部"
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

            int offset = toInt(arguments.get("offset"), 0);
            int limit = toInt(arguments.get("limit"), -1);

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

                long size = Files.size(filePath);
                if (size > properties.getMaxFileSize()) {
                    return ToolResult.error("File too large: " + size + " bytes (max: " + properties.getMaxFileSize() + ")");
                }

                // 判断文件类型
                String mime = detectMime(filePath);
                String content;

                if (XLSX_TYPES.contains(mime)) {
                    content = readXlsx(filePath);
                } else if (TIKA_TYPES.contains(mime)) {
                    content = readWithTika(filePath);
                } else {
                    // 文本文件：直接 UTF-8 读取
                    content = Files.readString(filePath, StandardCharsets.UTF_8);
                }

                // 分段截取
                if (offset > 0 || limit > 0) {
                    int totalLen = content.length();
                    int start = Math.min(offset, totalLen);
                    int end = limit > 0 ? Math.min(start + limit, totalLen) : totalLen;
                    String slice = content.substring(start, end);

                    String meta = String.format("[File: %s | Total: %d chars | Showing: %d-%d]\n",
                            filePath.getFileName(), totalLen, start, end);
                    log.info("Read file segment: {} (offset={}, limit={}, total={})", pathStr, start, limit, totalLen);
                    return ToolResult.success(meta + slice);
                }

                log.info("Read file: {} ({} chars, mime={})", pathStr, content.length(), mime);
                return ToolResult.success(content);

            } catch (Exception e) {
                log.error("Failed to read file: {}", pathStr, e);
                return ToolResult.error("Failed to read file: " + e.getMessage());
            }
        });
    }

    // ───────── 文件类型检测 ─────────

    private String detectMime(Path filePath) {
        // 优先按扩展名判断（更可靠）
        String fileName = filePath.getFileName().toString().toLowerCase();
        for (var entry : EXT_MIME.entrySet()) {
            if (fileName.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        // 兜底用 Tika 检测
        try {
            Tika tika = new Tika();
            return tika.detect(filePath);
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }

    // ───────── Tika 解析（PDF/DOCX/PPTX） ─────────

    private String readWithTika(Path filePath) throws Exception {
        Tika tika = new Tika();
        Metadata metadata = new Metadata();
        try (InputStream is = Files.newInputStream(filePath)) {
            String text = tika.parseToString(is, metadata);
            // 提取标题等元数据作为前缀
            String title = metadata.get("dc:title");
            StringBuilder sb = new StringBuilder();
            if (title != null && !title.isBlank()) {
                sb.append("[Title: ").append(title).append("]\n");
            }
            String pageCount = metadata.get("xmpTPg:NPages");
            if (pageCount != null) {
                sb.append("[Pages: ").append(pageCount).append("]\n");
            }
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(text);
            return sb.toString();
        }
    }

    // ───────── POI 解析（XLSX） ─────────

    private String readXlsx(Path filePath) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = Files.newInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(is)) {

            DataFormatter formatter = new DataFormatter();
            int sheetCount = workbook.getNumberOfSheets();

            for (int s = 0; s < sheetCount; s++) {
                Sheet sheet = workbook.getSheetAt(s);
                String sheetName = sheet.getSheetName();
                sb.append("=== Sheet: ").append(sheetName).append(" ===\n");

                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (Cell cell : row) {
                        cells.add(formatter.formatCellValue(cell));
                    }
                    sb.append(String.join("\t", cells)).append("\n");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    // ───────── 路径解析 ─────────

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
        Path globalWorkspace = WorkspaceResolver.resolveGlobalWorkspace(properties);

        // 1. 尝试 session workspace
        ToolExecutionContext ctx = ToolExecutionContext.current();
        if (ctx != null && ctx.getSessionId() != null) {
            Path sessionWorkspace = globalWorkspace.resolve(ctx.getSessionId()).normalize();
            Path sessionPath = sessionWorkspace.resolve(pathStr).normalize();
            if (sessionPath.startsWith(sessionWorkspace) && Files.exists(sessionPath)) {
                return sessionPath;
            }
        }

        // 2. Fallback 到全局 workspace（uploads 等）
        Path globalPath = globalWorkspace.resolve(pathStr).normalize();
        if (globalPath.startsWith(globalWorkspace)) {
            return globalPath;
        }

        // 3. 检查额外允许路径（skill 资源等）
        if (ctx != null) {
            Path absolutePath = Path.of(pathStr).toAbsolutePath().normalize();
            if (ctx.isPathAllowed(absolutePath)) {
                return absolutePath;
            }
        }

        return null;
    }

    private static int toInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
