# Phase 4: Memory 系统实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 实现 Markdown-as-source-of-truth 的**全局记忆系统**，写入不依赖 embedding，检索层可选启用向量搜索（有 embedding provider 就用，没有就关）。

**Architecture:**
- **全局记忆，跨会话** — 这是个人助手产品，不是多租户系统。所有记忆全局共享，今天对话中保存的偏好、明天的任何会话都能检索到。不存在"会话隔离"的概念。
- **Retain（写入）** = 纯 Markdown 文件操作，零 embedding 依赖。`MEMORY.md` 存核心长期记忆，`memory/YYYY-MM-DD.md` 存日记式追加日志。
- **Recall（检索）** = 三级降级策略：向量检索（pgvector，需 embedding provider）→ 全文检索（PostgreSQL tsvector，始终可用）→ 文件扫描（最后兜底）。Embedding provider 自动探测：本地模型 → OpenAI key → 其他 key → 全部没有则向量检索禁用，FTS 兜底。
- **索引是派生的** — Markdown 是真相源，`memory_chunks` 表是加速层，随时可从文件重建。

**Tech Stack:** Spring Boot 3, PostgreSQL + pgvector, Spring Data JPA, Flyway, WebClient (embedding API), Java NIO (file ops)

---

## 核心设计

### 关键设计原则：全局记忆

```
┌─────────────────────────────────────────────────────────────────────┐
│                    MiniClaw = 个人 AI 助手                           │
│                                                                      │
│  记忆是全局的、跨会话的：                                              │
│  - 会话 A 中保存的用户偏好 → 会话 B/C/D 都能检索到                     │
│  - 昨天的对话总结 → 今天随时可回忆                                     │
│  - MEMORY.md = 这个用户的"核心人格记忆"                               │
│  - 日记文件 = 按日期组织的全局历史记录                                 │
│                                                                      │
│  没有 session_id 隔离！这不是多租户系统。                              │
└─────────────────────────────────────────────────────────────────────┘
```

### 存储结构

```
workspace/
  memory/
    MEMORY.md           # 核心长期记忆（偏好、约束、关键事实）— 全局
    2026-01-15.md       # 日记式追加日志 — 全局
    2026-01-16.md
    ...
```

### 三级检索降级

```
用户调用 memory_search(query)
    │
    ├─ embedding provider 可用?
    │   ├─ YES → 向量检索 (pgvector cosine top-k) + FTS 补充 → 合并去重返回
    │   └─ NO  → 纯 FTS 检索 (tsvector) → 返回
    │
    └─ FTS 也无结果? → 返回空（或可选文件扫描兜底）

    注意：检索范围是全部记忆，不区分会话！
```

### Embedding Provider 自动探测

```
启动时检测（优先级从高到低）：
1. memory.embedding.provider = local  且 模型文件存在 → LocalEmbeddingProvider
2. memory.embedding.provider = openai 或 检测到 OPENAI_API_KEY → OpenAiEmbeddingProvider
3. memory.embedding.provider = llm    → 复用已有 LLM endpoint 的 /embeddings 接口
4. 以上都没有 → EmbeddingProvider = NONE，向量检索禁用，FTS 兜底

运行时状态可查：memory.status RPC 返回当前 provider 类型
```

### 数据模型

```
memory_chunks 表（派生索引，可重建）:
┌──────────────────────────────────────────────────────────────────────┐
│ id          │ VARCHAR(36) PK                                          │
│ file_path   │ TEXT        (相对于 workspace/memory/)                   │
│ line_start  │ INT         (chunk 起始行)                               │
│ line_end    │ INT         (chunk 结束行)                               │
│ content     │ TEXT        (chunk 原文)                                 │
│ embedding   │ vector(1536) NULL (向量，可为空)                         │
│ tsv         │ tsvector    (全文检索向量)                               │
│ created_at  │ TIMESTAMP                                                │
│ updated_at  │ TIMESTAMP                                                │
│                                                                        │
│ 注意：没有 session_id！记忆是全局的。                                   │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 任务分解

### Task P4-01: MemoryProperties 配置类

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/memory/MemoryProperties.java`
- Modify: `src/main/resources/application.yml`

**Step 1: 创建 MemoryProperties**

```java
package com.jaguarliu.ai.memory;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Memory 子系统配置
 *
 * 设计原则：记忆是全局的、跨会话的。
 * 这是个人助手，不是多租户系统。
 */
@Data
@Component
@ConfigurationProperties(prefix = "memory")
public class MemoryProperties {

    /**
     * 记忆文件存储目录（相对于 workspace 或绝对路径）
     * 默认: workspace/memory
     */
    private String path = "memory";

    /**
     * Chunking 配置
     */
    private ChunkConfig chunk = new ChunkConfig();

    /**
     * Embedding 配置
     */
    private EmbeddingConfig embedding = new EmbeddingConfig();

    /**
     * 检索配置
     */
    private SearchConfig search = new SearchConfig();

    /**
     * Pre-compaction flush 配置
     */
    private FlushConfig flush = new FlushConfig();

    @Data
    public static class ChunkConfig {
        /** 每个 chunk 的目标 token 数 */
        private int targetTokens = 400;
        /** chunk 间的重叠 token 数 */
        private int overlapTokens = 80;
    }

    @Data
    public static class EmbeddingConfig {
        /**
         * Embedding provider 类型：
         * - "auto"   : 自动探测（默认）
         * - "openai" : 使用 OpenAI embeddings API
         * - "llm"    : 复用 LLM endpoint 的 /embeddings 接口
         * - "none"   : 禁用向量检索，仅 FTS
         */
        private String provider = "auto";

        /** Embedding 模型名称（provider=openai/llm 时使用） */
        private String model = "text-embedding-3-small";

        /** Embedding API endpoint（provider=openai 时可覆盖） */
        private String endpoint;

        /** Embedding API Key（provider=openai 时可覆盖，默认复用 llm.api-key） */
        private String apiKey;

        /** Embedding 向量维度 */
        private int dimensions = 1536;

        /** 批量 embedding 大小 */
        private int batchSize = 20;
    }

    @Data
    public static class SearchConfig {
        /** 向量检索返回的 top-k */
        private int vectorTopK = 10;
        /** FTS 检索返回的 top-k */
        private int ftsTopK = 10;
        /** 合并后最终返回的 top-k */
        private int finalTopK = 5;
        /** snippet 最大字符数 */
        private int snippetMaxChars = 700;
        /** 最低相似度阈值（向量检索） */
        private double minSimilarity = 0.3;
    }

    @Data
    public static class FlushConfig {
        /** 是否启用 pre-compaction flush */
        private boolean enabled = true;
        /** 触发 flush 的 token 阈值（估算值） */
        private int tokenThreshold = 6000;
    }
}
```

**Step 2: 更新 application.yml**

在文件末尾追加：

```yaml
# Memory 配置（全局记忆，跨会话）
memory:
  path: memory  # 相对于 workspace
  chunk:
    target-tokens: 400
    overlap-tokens: 80
  embedding:
    provider: auto  # auto / openai / llm / none
    model: text-embedding-3-small
    dimensions: 1536
    batch-size: 20
  search:
    vector-top-k: 10
    fts-top-k: 10
    final-top-k: 5
    snippet-max-chars: 700
    min-similarity: 0.3
  flush:
    enabled: true
    token-threshold: 6000
```

**Step 3: 验证编译**

```bash
mvnw.cmd compile -q
```

**Step 4: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/memory/MemoryProperties.java src/main/resources/application.yml
git commit -m "feat(memory): [P4-01] MemoryProperties 配置类"
```

---

### Task P4-02: MemoryStore（Markdown 文件读写）

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/memory/store/MemoryStore.java`

**核心职责：** 记忆的写入（Retain）层。纯文件操作，零 embedding 依赖。全局存储，不区分会话。

```java
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
```

**Step: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/memory/store/MemoryStore.java
git commit -m "feat(memory): [P4-02] MemoryStore 全局 Markdown 文件读写"
```

---

### Task P4-03: MemoryChunker（文本分块）

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/memory/index/MemoryChunk.java`
- Create: `src/main/java/com/jaguarliu/ai/memory/index/MemoryChunker.java`

**Step 1: MemoryChunk 数据模型**

```java
package com.jaguarliu.ai.memory.index;

import lombok.Builder;
import lombok.Data;

/**
 * 记忆文本块
 * Chunking 产出的最小检索单元
 */
@Data
@Builder
public class MemoryChunk {
    /** 来源文件（相对于 memory 目录） */
    private String filePath;
    /** 起始行号（1-based） */
    private int lineStart;
    /** 结束行号（1-based，含） */
    private int lineEnd;
    /** chunk 原文 */
    private String content;
}
```

**Step 2: MemoryChunker**

```java
package com.jaguarliu.ai.memory.index;

import com.jaguarliu.ai.memory.MemoryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 记忆文本分块器
 *
 * 策略：
 * - 按段落（Markdown 空行）为自然边界
 * - 目标 ~400 tokens/chunk，overlap ~80 tokens
 * - token 估算：中文字符 ×2，其他字符 ×0.3
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryChunker {

    private final MemoryProperties properties;

    /**
     * 将文件内容切分为 chunks
     *
     * @param filePath 文件相对路径
     * @param content  文件内容
     * @return chunk 列表
     */
    public List<MemoryChunk> chunk(String filePath, String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        int targetTokens = properties.getChunk().getTargetTokens();
        int overlapTokens = properties.getChunk().getOverlapTokens();

        String[] lines = content.split("\n", -1);
        List<MemoryChunk> chunks = new ArrayList<>();

        int currentStart = 0; // 0-based line index

        while (currentStart < lines.length) {
            // 向前累积直到达到目标 token 数
            int currentEnd = currentStart;
            int tokenCount = 0;

            while (currentEnd < lines.length && tokenCount < targetTokens) {
                tokenCount += estimateTokens(lines[currentEnd]);
                currentEnd++;
            }

            // 提取 chunk 内容
            StringBuilder chunkContent = new StringBuilder();
            for (int i = currentStart; i < currentEnd; i++) {
                if (i > currentStart) chunkContent.append("\n");
                chunkContent.append(lines[i]);
            }

            String text = chunkContent.toString().trim();
            if (!text.isEmpty()) {
                chunks.add(MemoryChunk.builder()
                        .filePath(filePath)
                        .lineStart(currentStart + 1)   // 转 1-based
                        .lineEnd(currentEnd)            // 已经是下一行，刚好是含末行的 1-based
                        .content(text)
                        .build());
            }

            // 计算下一个 chunk 的起始位置（回退 overlap）
            int overlapLines = 0;
            int overlapCount = 0;
            for (int i = currentEnd - 1; i > currentStart && overlapCount < overlapTokens; i--) {
                overlapCount += estimateTokens(lines[i]);
                overlapLines++;
            }

            currentStart = currentEnd - overlapLines;
            // 防止无进展
            if (currentStart <= (currentEnd - (currentEnd - currentStart))) {
                currentStart = currentEnd;
            }
        }

        log.debug("Chunked {}: {} chunks from {} lines", filePath, chunks.size(), lines.length);
        return chunks;
    }

    /**
     * 估算单行 token 数
     * 简单规则：中文字符 ×2，其他字符 ×0.3
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 1; // 空行算 1 token

        int chineseCount = 0;
        int otherCount = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseCount++;
            } else {
                otherCount++;
            }
        }
        return Math.max(1, (int) (chineseCount * 2 + otherCount * 0.3));
    }
}
```

**Step: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/memory/index/
git commit -m "feat(memory): [P4-03] MemoryChunker 文本分块器"
```

---

### Task P4-04: EmbeddingClient 接口与实现

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/memory/embedding/EmbeddingClient.java`
- Create: `src/main/java/com/jaguarliu/ai/memory/embedding/EmbeddingResult.java`
- Create: `src/main/java/com/jaguarliu/ai/memory/embedding/OpenAiEmbeddingClient.java`
- Create: `src/main/java/com/jaguarliu/ai/memory/embedding/EmbeddingProviderFactory.java`

**Step 1: EmbeddingResult**

```java
package com.jaguarliu.ai.memory.embedding;

import lombok.Data;
import lombok.Builder;

import java.util.List;

/**
 * Embedding 结果
 */
@Data
@Builder
public class EmbeddingResult {
    /** 向量 */
    private List<Float> embedding;
    /** 使用的 token 数 */
    private int tokenCount;
}
```

**Step 2: EmbeddingClient 接口**

```java
package com.jaguarliu.ai.memory.embedding;

import java.util.List;

/**
 * Embedding 客户端接口
 *
 * 设计原则：
 * - 这是一个"可选"组件 — 没有 provider 时系统正常运行
 * - 实现类由 EmbeddingProviderFactory 根据配置自动选择
 */
public interface EmbeddingClient {

    /**
     * 对单段文本生成 embedding
     */
    EmbeddingResult embed(String text);

    /**
     * 批量生成 embedding
     */
    List<EmbeddingResult> embedBatch(List<String> texts);

    /**
     * 获取向量维度
     */
    int getDimensions();

    /**
     * 获取 provider 名称
     */
    String getProviderName();
}
```

**Step 3: OpenAiEmbeddingClient（兼容 OpenAI /embeddings 接口）**

```java
package com.jaguarliu.ai.memory.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容的 Embedding 客户端
 *
 * 支持所有 OpenAI 兼容接口：
 * - OpenAI
 * - DeepSeek
 * - 通义千问 (dashscope compatible-mode)
 * - Ollama
 * - 其他兼容实现
 */
@Slf4j
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private final WebClient webClient;
    private final String model;
    private final int dimensions;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiEmbeddingClient(String endpoint, String apiKey, String model, int dimensions) {
        this.model = model;
        this.dimensions = dimensions;

        // 规范化 endpoint
        String baseUrl = normalizeEndpoint(endpoint);

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(16 * 1024 * 1024))
                .build();

        log.info("OpenAiEmbeddingClient initialized: endpoint={}, model={}, dimensions={}",
                baseUrl, model, dimensions);
    }

    @Override
    public EmbeddingResult embed(String text) {
        List<EmbeddingResult> results = embedBatch(List.of(text));
        return results.isEmpty() ? null : results.getFirst();
    }

    @Override
    public List<EmbeddingResult> embedBatch(List<String> texts) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "input", texts
        );

        try {
            String response = webClient.post()
                    .uri("/embeddings")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseResponse(response);

        } catch (Exception e) {
            log.error("Embedding request failed: {}", e.getMessage(), e);
            throw new RuntimeException("Embedding failed: " + e.getMessage(), e);
        }
    }

    @Override
    public int getDimensions() {
        return dimensions;
    }

    @Override
    public String getProviderName() {
        return "openai-compatible";
    }

    private List<EmbeddingResult> parseResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.get("data");

            List<EmbeddingResult> results = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode embeddingNode = item.get("embedding");
                List<Float> vector = new ArrayList<>();
                for (JsonNode val : embeddingNode) {
                    vector.add(val.floatValue());
                }
                results.add(EmbeddingResult.builder()
                        .embedding(vector)
                        .tokenCount(root.has("usage") ? root.get("usage").get("total_tokens").asInt() : 0)
                        .build());
            }
            return results;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse embedding response: " + e.getMessage(), e);
        }
    }

    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null) return "http://localhost:11434/v1";
        endpoint = endpoint.replaceAll("/+$", "");
        // 如果已含 /v* 路径，不再追加
        if (endpoint.matches(".*/(v\\d+)$")) {
            return endpoint;
        }
        return endpoint + "/v1";
    }
}
```

**Step 4: EmbeddingProviderFactory（自动探测）**

```java
package com.jaguarliu.ai.memory.embedding;

import com.jaguarliu.ai.llm.LlmProperties;
import com.jaguarliu.ai.memory.MemoryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Embedding Provider 工厂
 *
 * 自动探测策略（优先级从高到低）：
 * 1. 显式配置 memory.embedding.provider = openai/llm/none
 * 2. auto 模式下自动探测：
 *    a. memory.embedding.endpoint + apiKey 存在 → OpenAI 兼容
 *    b. llm.endpoint + llm.api-key 存在 → 复用 LLM 的 endpoint
 *    c. 都没有 → 返回 empty（向量检索禁用）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingProviderFactory {

    private final MemoryProperties memoryProperties;
    private final LlmProperties llmProperties;

    /**
     * 创建 EmbeddingClient
     *
     * @return 如果能创建返回客户端，否则返回 empty（向量检索将禁用）
     */
    public Optional<EmbeddingClient> create() {
        MemoryProperties.EmbeddingConfig config = memoryProperties.getEmbedding();
        String providerType = config.getProvider();

        return switch (providerType) {
            case "none" -> {
                log.info("Embedding provider explicitly disabled (provider=none)");
                yield Optional.empty();
            }
            case "openai" -> createOpenAi(config);
            case "llm" -> createFromLlm(config);
            case "auto" -> autoDetect(config);
            default -> {
                log.warn("Unknown embedding provider: {}, falling back to auto", providerType);
                yield autoDetect(config);
            }
        };
    }

    /**
     * 自动探测
     */
    private Optional<EmbeddingClient> autoDetect(MemoryProperties.EmbeddingConfig config) {
        log.info("Auto-detecting embedding provider...");

        // 1. 检查是否有专用 embedding 配置
        if (isNotBlank(config.getEndpoint()) && isNotBlank(config.getApiKey())) {
            log.info("Detected dedicated embedding endpoint");
            return createOpenAi(config);
        }

        // 2. 检查是否能复用 LLM endpoint
        if (isNotBlank(llmProperties.getEndpoint()) && isNotBlank(llmProperties.getApiKey())) {
            log.info("Detected LLM endpoint, reusing for embeddings");
            return createFromLlm(config);
        }

        // 3. 都没有 → 禁用
        log.info("No embedding provider available. Vector search disabled, FTS fallback active.");
        return Optional.empty();
    }

    private Optional<EmbeddingClient> createOpenAi(MemoryProperties.EmbeddingConfig config) {
        String endpoint = isNotBlank(config.getEndpoint()) ? config.getEndpoint() : llmProperties.getEndpoint();
        String apiKey = isNotBlank(config.getApiKey()) ? config.getApiKey() : llmProperties.getApiKey();

        if (!isNotBlank(endpoint) || !isNotBlank(apiKey)) {
            log.warn("OpenAI embedding config incomplete, disabling vector search");
            return Optional.empty();
        }

        try {
            OpenAiEmbeddingClient client = new OpenAiEmbeddingClient(
                    endpoint, apiKey, config.getModel(), config.getDimensions());
            log.info("Created OpenAI-compatible embedding client: endpoint={}, model={}",
                    endpoint, config.getModel());
            return Optional.of(client);
        } catch (Exception e) {
            log.error("Failed to create embedding client: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<EmbeddingClient> createFromLlm(MemoryProperties.EmbeddingConfig config) {
        if (!isNotBlank(llmProperties.getEndpoint()) || !isNotBlank(llmProperties.getApiKey())) {
            log.warn("LLM config not available for embedding, disabling vector search");
            return Optional.empty();
        }

        try {
            OpenAiEmbeddingClient client = new OpenAiEmbeddingClient(
                    llmProperties.getEndpoint(),
                    llmProperties.getApiKey(),
                    config.getModel(),
                    config.getDimensions());
            log.info("Created embedding client from LLM config: endpoint={}, model={}",
                    llmProperties.getEndpoint(), config.getModel());
            return Optional.of(client);
        } catch (Exception e) {
            log.error("Failed to create embedding client from LLM: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
```

**Step: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/memory/embedding/
git commit -m "feat(memory): [P4-04] EmbeddingClient 接口与 OpenAI 兼容实现 + 自动探测工厂"
```

---

### Task P4-05: 数据库迁移（memory_chunks 表）

**Files:**
- Create: `src/main/resources/db/migration/V2__memory_chunks.sql`
- Create: `src/main/java/com/jaguarliu/ai/memory/index/MemoryChunkEntity.java`
- Create: `src/main/java/com/jaguarliu/ai/memory/index/MemoryChunkRepository.java`

**Step 1: Flyway 迁移脚本**

```sql
-- V2__memory_chunks.sql
-- Memory 子系统：全局记忆 chunk 索引表（派生索引，可从 Markdown 重建）
-- 注意：没有 session_id！记忆是全局的、跨会话的。

CREATE TABLE memory_chunks (
    id          VARCHAR(36) PRIMARY KEY,
    file_path   TEXT        NOT NULL,  -- 相对于 workspace/memory/ 的路径
    line_start  INT         NOT NULL,  -- chunk 起始行 (1-based)
    line_end    INT         NOT NULL,  -- chunk 结束行 (1-based，含)
    content     TEXT        NOT NULL,  -- chunk 原文
    embedding   vector(1536),          -- 向量（可为 NULL，无 embedding provider 时）
    tsv         tsvector,              -- 全文检索向量（始终填充）
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 向量检索索引（IVFFlat，适合中小数据量）
CREATE INDEX idx_memory_chunks_embedding ON memory_chunks
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 10);

-- 全文检索索引（GIN，始终可用）
CREATE INDEX idx_memory_chunks_tsv ON memory_chunks USING GIN (tsv);

-- 文件路径索引（用于按文件更新/删除）
CREATE INDEX idx_memory_chunks_file_path ON memory_chunks (file_path);

-- tsvector 自动更新触发器
-- 使用 'simple' 配置，对中英文都友好
CREATE OR REPLACE FUNCTION memory_chunks_tsv_trigger() RETURNS trigger AS $$
BEGIN
    NEW.tsv := to_tsvector('simple', NEW.content);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_memory_chunks_tsv
    BEFORE INSERT OR UPDATE ON memory_chunks
    FOR EACH ROW EXECUTE FUNCTION memory_chunks_tsv_trigger();
```

**Step 2: JPA Entity**

```java
package com.jaguarliu.ai.memory.index;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * memory_chunks 表实体
 *
 * 全局记忆索引，不区分会话（个人助手，非多租户）
 *
 * 注意：embedding 和 tsv 字段由原生 SQL 操作，
 * JPA 只管理基础字段。向量操作通过 Native Query 完成。
 */
@Entity
@Table(name = "memory_chunks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryChunkEntity {

    @Id
    private String id;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "line_start", nullable = false)
    private int lineStart;

    @Column(name = "line_end", nullable = false)
    private int lineEnd;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // embedding 和 tsv 由 Native Query 操作，不映射到 JPA
    // tsv 由数据库触发器自动填充

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

**Step 3: Repository**

```java
package com.jaguarliu.ai.memory.index;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * memory_chunks 存储库
 *
 * 全局记忆检索，不区分会话。
 * 向量检索和 FTS 使用 Native Query（JPA 不直接支持 pgvector）
 */
@Repository
public interface MemoryChunkRepository extends JpaRepository<MemoryChunkEntity, String> {

    /**
     * 按文件路径查找所有 chunks
     */
    List<MemoryChunkEntity> findByFilePath(String filePath);

    /**
     * 删除指定文件的所有 chunks
     */
    @Transactional
    @Modifying
    void deleteByFilePath(String filePath);

    /**
     * 删除所有 chunks（重建索引时使用）
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM MemoryChunkEntity")
    void deleteAllChunks();

    /**
     * 向量检索（余弦相似度）- 全局检索
     * 注意：embedding 参数以 [0.1,0.2,...] 字符串格式传入
     */
    @Query(value = """
        SELECT id, file_path, line_start, line_end, content,
               1 - (embedding <=> cast(:embedding as vector)) AS similarity
        FROM memory_chunks
        WHERE embedding IS NOT NULL
        ORDER BY embedding <=> cast(:embedding as vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> searchByVector(@Param("embedding") String embedding, @Param("limit") int limit);

    /**
     * 全文检索 - 全局检索
     */
    @Query(value = """
        SELECT id, file_path, line_start, line_end, content,
               ts_rank(tsv, plainto_tsquery('simple', :query)) AS rank
        FROM memory_chunks
        WHERE tsv @@ plainto_tsquery('simple', :query)
        ORDER BY rank DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> searchByFts(@Param("query") String query, @Param("limit") int limit);

    /**
     * 更新 chunk 的 embedding（Native SQL，因为 JPA 不支持 vector 类型）
     */
    @Transactional
    @Modifying
    @Query(value = """
        UPDATE memory_chunks SET embedding = cast(:embedding as vector), updated_at = NOW()
        WHERE id = :id
        """, nativeQuery = true)
    void updateEmbedding(@Param("id") String id, @Param("embedding") String embedding);

    /**
     * 统计有 embedding 的 chunk 数量
     */
    @Query(value = "SELECT COUNT(*) FROM memory_chunks WHERE embedding IS NOT NULL", nativeQuery = true)
    long countWithEmbedding();

    /**
     * 统计总 chunk 数量
     */
    @Query(value = "SELECT COUNT(*) FROM memory_chunks", nativeQuery = true)
    long countTotal();
}
```

**Step: Commit**

```bash
git add src/main/resources/db/migration/V2__memory_chunks.sql
git add src/main/java/com/jaguarliu/ai/memory/index/MemoryChunkEntity.java
git add src/main/java/com/jaguarliu/ai/memory/index/MemoryChunkRepository.java
git commit -m "feat(memory): [P4-05] memory_chunks 表迁移（全局记忆，无 session_id）"
```

---

### Task P4-06: MemoryIndexer（分块 + 入库 + 可选 embedding）

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/memory/index/MemoryIndexer.java`

**核心设计：** 索引是派生的。写入时只做 chunking + FTS 入库。Embedding 作为异步增强，有 provider 就做，没有就跳过。

```java
package com.jaguarliu.ai.memory.index;

import com.jaguarliu.ai.memory.MemoryProperties;
import com.jaguarliu.ai.memory.embedding.EmbeddingClient;
import com.jaguarliu.ai.memory.embedding.EmbeddingProviderFactory;
import com.jaguarliu.ai.memory.embedding.EmbeddingResult;
import com.jaguarliu.ai.memory.store.MemoryStore;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 全局记忆索引器
 *
 * 职责：
 * 1. 将 Markdown 文件分块 → 写入 memory_chunks 表
 * 2. 如果 embedding provider 可用 → 生成向量并写入
 * 3. FTS (tsvector) 由数据库触发器自动填充
 *
 * 设计原则：
 * - 记忆是全局的、跨会话的
 * - Markdown 是真相源，索引是派生的
 * - 索引可随时从 Markdown 重建（rebuild）
 * - Embedding 是可选加速层：有就用，没有就关
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryIndexer {

    private final MemoryStore memoryStore;
    private final MemoryChunker chunker;
    private final MemoryChunkRepository chunkRepository;
    private final EmbeddingProviderFactory embeddingFactory;
    private final MemoryProperties properties;

    /** 当前可用的 Embedding 客户端（可能为空） */
    @Getter
    private EmbeddingClient embeddingClient;

    /** 向量检索是否可用 */
    @Getter
    private boolean vectorSearchEnabled;

    @PostConstruct
    public void init() {
        Optional<EmbeddingClient> client = embeddingFactory.create();
        if (client.isPresent()) {
            this.embeddingClient = client.get();
            this.vectorSearchEnabled = true;
            log.info("MemoryIndexer initialized with embedding provider: {}",
                    embeddingClient.getProviderName());
        } else {
            this.vectorSearchEnabled = false;
            log.info("MemoryIndexer initialized WITHOUT embedding. FTS-only mode for global memory.");
        }
    }

    /**
     * 索引指定文件（全局记忆）
     * 删除旧 chunks → 重新分块 → 入库 → 可选 embedding
     */
    public void indexFile(String relativePath) {
        log.info("Indexing global memory file: {}", relativePath);

        try {
            String content = memoryStore.read(relativePath);

            // 1. 删除旧 chunks
            chunkRepository.deleteByFilePath(relativePath);

            // 2. 分块
            List<MemoryChunk> chunks = chunker.chunk(relativePath, content);
            if (chunks.isEmpty()) {
                log.debug("No chunks generated for: {}", relativePath);
                return;
            }

            // 3. 写入 memory_chunks 表（FTS 由触发器自动填充）
            List<MemoryChunkEntity> entities = chunks.stream()
                    .map(c -> MemoryChunkEntity.builder()
                            .id(UUID.randomUUID().toString())
                            .filePath(c.getFilePath())
                            .lineStart(c.getLineStart())
                            .lineEnd(c.getLineEnd())
                            .content(c.getContent())
                            .build())
                    .toList();

            chunkRepository.saveAll(entities);
            log.info("Indexed {} chunks for global memory: {}", entities.size(), relativePath);

            // 4. 如果有 embedding provider，异步生成向量
            if (vectorSearchEnabled) {
                generateEmbeddings(entities);
            }

        } catch (IOException e) {
            log.error("Failed to index file: {}", relativePath, e);
        }
    }

    /**
     * 重建全部索引（全局记忆）
     * 从 Markdown 文件重新生成所有 chunks
     */
    public void rebuild() {
        log.info("Rebuilding global memory index from Markdown source...");

        // 1. 清空所有 chunks
        chunkRepository.deleteAllChunks();

        // 2. 列出所有记忆文件
        try {
            List<MemoryStore.MemoryFileInfo> files = memoryStore.listFiles();
            for (MemoryStore.MemoryFileInfo file : files) {
                indexFile(file.relativePath());
            }
            log.info("Global memory index rebuild complete: {} files processed", files.size());
        } catch (IOException e) {
            log.error("Failed to rebuild memory index", e);
        }
    }

    /**
     * 删除指定文件的索引
     */
    public void removeFile(String relativePath) {
        chunkRepository.deleteByFilePath(relativePath);
        log.info("Removed index for: {}", relativePath);
    }

    /**
     * 为指定 entities 生成 embedding 并写入
     */
    private void generateEmbeddings(List<MemoryChunkEntity> entities) {
        if (embeddingClient == null) return;

        int batchSize = properties.getEmbedding().getBatchSize();

        for (int i = 0; i < entities.size(); i += batchSize) {
            int end = Math.min(i + batchSize, entities.size());
            List<MemoryChunkEntity> batch = entities.subList(i, end);

            try {
                List<String> texts = batch.stream()
                        .map(MemoryChunkEntity::getContent)
                        .toList();

                List<EmbeddingResult> results = embeddingClient.embedBatch(texts);

                for (int j = 0; j < batch.size() && j < results.size(); j++) {
                    String vectorStr = results.get(j).getEmbedding().stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(",", "[", "]"));

                    chunkRepository.updateEmbedding(batch.get(j).getId(), vectorStr);
                }

                log.debug("Generated embeddings for batch {}-{}", i, end);

            } catch (Exception e) {
                log.warn("Failed to generate embeddings for batch {}-{}: {}",
                        i, end, e.getMessage());
                // 不抛异常 — embedding 失败不影响基础功能
            }
        }
    }

    /**
     * 获取索引状态
     */
    public IndexStatus getStatus() {
        long total = chunkRepository.countTotal();
        long withEmbedding = vectorSearchEnabled ? chunkRepository.countWithEmbedding() : 0;

        return new IndexStatus(
                total,
                withEmbedding,
                vectorSearchEnabled,
                embeddingClient != null ? embeddingClient.getProviderName() : "none"
        );
    }

    public record IndexStatus(
            long totalChunks,
            long chunksWithEmbedding,
            boolean vectorSearchEnabled,
            String embeddingProvider
    ) {}
}
```

**Step: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/memory/index/MemoryIndexer.java
git commit -m "feat(memory): [P4-06] MemoryIndexer 全局记忆分块入库 + 可选 embedding"
```

---

### Task P4-07: MemorySearchService（三级降级检索）

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/memory/search/SearchResult.java`
- Create: `src/main/java/com/jaguarliu/ai/memory/search/MemorySearchService.java`

**Step 1: SearchResult**

```java
package com.jaguarliu.ai.memory.search;

import lombok.Builder;
import lombok.Data;

/**
 * 记忆检索结果
 */
@Data
@Builder
public class SearchResult {
    /** 来源文件路径（相对于 memory 目录） */
    private String filePath;
    /** 起始行号 */
    private int lineStart;
    /** 结束行号 */
    private int lineEnd;
    /** 内容片段（截断到 snippetMaxChars） */
    private String snippet;
    /** 相关性评分 (0~1) */
    private double score;
    /** 检索来源（vector / fts） */
    private String source;
}
```

**Step 2: MemorySearchService**

```java
package com.jaguarliu.ai.memory.search;

import com.jaguarliu.ai.memory.MemoryProperties;
import com.jaguarliu.ai.memory.embedding.EmbeddingClient;
import com.jaguarliu.ai.memory.embedding.EmbeddingResult;
import com.jaguarliu.ai.memory.index.MemoryChunkRepository;
import com.jaguarliu.ai.memory.index.MemoryIndexer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 全局记忆检索服务
 *
 * 检索范围：所有历史记忆（全局，跨会话）
 *
 * 三级降级策略：
 * 1. 向量检索（需要 embedding provider）
 * 2. 全文检索（PostgreSQL tsvector，始终可用）
 * 3. 合并去重 → 返回 top-k
 *
 * 如果 embedding provider 不可用，只走 FTS。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemorySearchService {

    private final MemoryChunkRepository chunkRepository;
    private final MemoryIndexer indexer;
    private final MemoryProperties properties;

    /**
     * 语义检索全局记忆
     *
     * @param query 检索查询
     * @return 排序后的检索结果（全局，不区分会话）
     */
    public List<SearchResult> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        MemoryProperties.SearchConfig config = properties.getSearch();
        Map<String, SearchResult> resultMap = new LinkedHashMap<>();

        // 1. 向量检索（如果可用）
        if (indexer.isVectorSearchEnabled()) {
            try {
                List<SearchResult> vectorResults = searchByVector(query, config.getVectorTopK());
                for (SearchResult r : vectorResults) {
                    if (r.getScore() >= config.getMinSimilarity()) {
                        String key = r.getFilePath() + ":" + r.getLineStart();
                        resultMap.putIfAbsent(key, r);
                    }
                }
                log.debug("Vector search returned {} results", vectorResults.size());
            } catch (Exception e) {
                log.warn("Vector search failed, falling back to FTS: {}", e.getMessage());
            }
        }

        // 2. FTS 检索（始终执行，补充向量检索的遗漏）
        try {
            List<SearchResult> ftsResults = searchByFts(query, config.getFtsTopK());
            for (SearchResult r : ftsResults) {
                String key = r.getFilePath() + ":" + r.getLineStart();
                resultMap.putIfAbsent(key, r); // 不覆盖已有的向量结果（分数更准确）
            }
            log.debug("FTS search returned {} results", ftsResults.size());
        } catch (Exception e) {
            log.warn("FTS search failed: {}", e.getMessage());
        }

        // 3. 合并排序 → 返回 top-k
        List<SearchResult> merged = resultMap.values().stream()
                .sorted(Comparator.comparingDouble(SearchResult::getScore).reversed())
                .limit(config.getFinalTopK())
                .toList();

        log.info("Global memory search for '{}': {} results (vector={}, fts=merged)",
                truncate(query, 50), merged.size(), indexer.isVectorSearchEnabled());

        return merged;
    }

    /**
     * 向量检索
     */
    private List<SearchResult> searchByVector(String query, int topK) {
        EmbeddingClient embeddingClient = indexer.getEmbeddingClient();
        if (embeddingClient == null) {
            return List.of();
        }

        EmbeddingResult queryEmbedding = embeddingClient.embed(query);
        if (queryEmbedding == null || queryEmbedding.getEmbedding() == null) {
            return List.of();
        }

        String vectorStr = queryEmbedding.getEmbedding().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));

        List<Object[]> rows = chunkRepository.searchByVector(vectorStr, topK);
        int snippetMax = properties.getSearch().getSnippetMaxChars();

        return rows.stream()
                .map(row -> SearchResult.builder()
                        .filePath((String) row[1])
                        .lineStart(((Number) row[2]).intValue())
                        .lineEnd(((Number) row[3]).intValue())
                        .snippet(truncate((String) row[4], snippetMax))
                        .score(((Number) row[5]).doubleValue())
                        .source("vector")
                        .build())
                .toList();
    }

    /**
     * 全文检索
     */
    private List<SearchResult> searchByFts(String query, int topK) {
        List<Object[]> rows = chunkRepository.searchByFts(query, topK);
        int snippetMax = properties.getSearch().getSnippetMaxChars();

        return rows.stream()
                .map(row -> SearchResult.builder()
                        .filePath((String) row[1])
                        .lineStart(((Number) row[2]).intValue())
                        .lineEnd(((Number) row[3]).intValue())
                        .snippet(truncate((String) row[4], snippetMax))
                        .score(normalizeRank(((Number) row[5]).doubleValue()))
                        .source("fts")
                        .build())
                .toList();
    }

    /**
     * 将 FTS rank 归一化到 0~1 区间
     */
    private double normalizeRank(double rank) {
        // ts_rank 通常在 0~1 之间，但不保证
        return Math.min(1.0, Math.max(0.0, rank));
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
```

**Step: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/memory/search/
git commit -m "feat(memory): [P4-07] MemorySearchService 全局记忆三级降级检索"
```

---

### Task P4-08: memory_search 工具

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/tools/builtin/MemorySearchTool.java`

```java
package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.memory.search.MemorySearchService;
import com.jaguarliu.ai.memory.search.SearchResult;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * memory_search 工具
 *
 * 语义检索全局记忆，返回 snippet + 文件路径 + 行号 + 相关性评分。
 * 检索范围是所有历史记忆（跨会话），体现"个人助手"的设计理念。
 * 内部自动走向量+FTS 混合检索（有 embedding 就走向量，没有就纯 FTS）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemorySearchTool implements Tool {

    private final MemorySearchService searchService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("memory_search")
                .description("搜索全局记忆。检索之前所有对话中保存的信息：用户偏好、关键事实、工作总结等。记忆是跨会话的，今天能检索到昨天保存的内容。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of(
                                        "type", "string",
                                        "description", "检索查询（自然语言描述你想找的信息）"
                                )
                        ),
                        "required", List.of("query")
                ))
                .hitl(false)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String query = (String) arguments.get("query");
            if (query == null || query.isBlank()) {
                return ToolResult.error("Missing required parameter: query");
            }

            List<SearchResult> results = searchService.search(query);

            if (results.isEmpty()) {
                return ToolResult.success("No matching memories found for: " + query);
            }

            // 格式化输出
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Found %d memory fragments:\n\n", results.size()));

            for (int i = 0; i < results.size(); i++) {
                SearchResult r = results.get(i);
                sb.append(String.format("--- [%d] %s (lines %d-%d, score: %.2f, via: %s) ---\n",
                        i + 1, r.getFilePath(), r.getLineStart(), r.getLineEnd(),
                        r.getScore(), r.getSource()));
                sb.append(r.getSnippet());
                sb.append("\n\n");
            }

            return ToolResult.success(sb.toString().trim());
        });
    }
}
```

**Step: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/tools/builtin/MemorySearchTool.java
git commit -m "feat(memory): [P4-08] memory_search 工具（全局记忆检索）"
```

---

### Task P4-09: memory_get 工具

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/tools/builtin/MemoryGetTool.java`

```java
package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.memory.store.MemoryStore;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * memory_get 工具
 *
 * 按路径读取指定记忆文件，支持行号范围。
 * 用于在 memory_search 返回定位后，精确获取上下文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryGetTool implements Tool {

    private final MemoryStore memoryStore;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("memory_get")
                .description("读取指定记忆文件的内容。可选指定起始行和行数。通常配合 memory_search 使用，在搜索定位后获取完整上下文。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "path", Map.of(
                                        "type", "string",
                                        "description", "记忆文件路径（相对于 memory 目录，如 MEMORY.md 或 2026-01-15.md）"
                                ),
                                "line", Map.of(
                                        "type", "integer",
                                        "description", "起始行号（1-based，可选，默认从头开始）"
                                ),
                                "limit", Map.of(
                                        "type", "integer",
                                        "description", "读取行数（可选，默认 50 行）"
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
            String path = (String) arguments.get("path");
            if (path == null || path.isBlank()) {
                return ToolResult.error("Missing required parameter: path");
            }

            int line = arguments.containsKey("line")
                    ? ((Number) arguments.get("line")).intValue() : 1;
            int limit = arguments.containsKey("limit")
                    ? ((Number) arguments.get("limit")).intValue() : 50;

            try {
                String content;
                if (line <= 1 && limit >= 9999) {
                    // 读取整个文件
                    content = memoryStore.read(path);
                } else {
                    content = memoryStore.readLines(path, line, limit);
                }

                if (content.isEmpty()) {
                    return ToolResult.success("(empty content)");
                }

                // 添加元信息头
                String header = String.format("--- %s (from line %d, %d lines) ---\n",
                        path, line, limit);
                return ToolResult.success(header + content);

            } catch (Exception e) {
                return ToolResult.error("Failed to read memory: " + e.getMessage());
            }
        });
    }
}
```

**Step: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/tools/builtin/MemoryGetTool.java
git commit -m "feat(memory): [P4-09] memory_get 工具"
```

---

### Task P4-10: memory_write 工具（Agent 主动写入记忆）

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/tools/builtin/MemoryWriteTool.java`

```java
package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.memory.index.MemoryIndexer;
import com.jaguarliu.ai.memory.store.MemoryStore;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * memory_write 工具
 *
 * 将信息写入全局记忆（Markdown 追加）。
 * 写入的内容是全局的、跨会话的，任何后续会话都能检索到。
 *
 * 两种目标：
 * - "core": 写入 MEMORY.md（长期记忆：偏好、约束、关键事实）
 * - "daily": 写入今日日记（对话总结、工作记录）
 *
 * 写入后自动触发该文件的索引更新。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryWriteTool implements Tool {

    private final MemoryStore memoryStore;
    private final MemoryIndexer indexer;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("memory_write")
                .description("将信息写入全局记忆（跨会话）。用于保存用户偏好、关键事实、对话总结等。写入后任何后续会话都能检索到。支持写入核心记忆(MEMORY.md)或今日日记。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "content", Map.of(
                                        "type", "string",
                                        "description", "要写入的内容（Markdown 格式）"
                                ),
                                "target", Map.of(
                                        "type", "string",
                                        "enum", List.of("core", "daily"),
                                        "description", "写入目标：core=核心长期记忆(MEMORY.md), daily=今日日记"
                                )
                        ),
                        "required", List.of("content", "target")
                ))
                .hitl(false)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            String content = (String) arguments.get("content");
            String target = (String) arguments.get("target");

            if (content == null || content.isBlank()) {
                return ToolResult.error("Missing required parameter: content");
            }
            if (target == null || target.isBlank()) {
                target = "daily"; // 默认写日记
            }

            try {
                String filePath;
                if ("core".equals(target)) {
                    memoryStore.appendToCore(content);
                    filePath = "MEMORY.md";
                } else {
                    memoryStore.appendToDaily(content);
                    filePath = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".md";
                }

                // 触发索引更新
                indexer.indexFile(filePath);

                return ToolResult.success("Memory written to global " + filePath + " (searchable in all sessions)");

            } catch (Exception e) {
                log.error("Failed to write memory: {}", e.getMessage(), e);
                return ToolResult.error("Failed to write memory: " + e.getMessage());
            }
        });
    }
}
```

**Step: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/tools/builtin/MemoryWriteTool.java
git commit -m "feat(memory): [P4-10] memory_write 工具（全局记忆写入）"
```

---

### Task P4-11: PreCompactionFlushHook

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/memory/flush/PreCompactionFlushHook.java`

```java
package com.jaguarliu.ai.memory.flush;

import com.jaguarliu.ai.llm.LlmClient;
import com.jaguarliu.ai.llm.model.LlmRequest;
import com.jaguarliu.ai.llm.model.LlmResponse;
import com.jaguarliu.ai.memory.MemoryProperties;
import com.jaguarliu.ai.memory.index.MemoryChunker;
import com.jaguarliu.ai.memory.index.MemoryIndexer;
import com.jaguarliu.ai.memory.store.MemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pre-Compaction Memory Flush
 *
 * 当 token 估算接近窗口阈值时：
 * 1. 强制让 LLM 总结当前对话关键状态
 * 2. 写入全局记忆 memory/YYYY-MM-DD.md
 * 3. 标记本次 run 已 flush（避免重复）
 *
 * 写入的总结是全局的，后续任何会话都能检索到。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreCompactionFlushHook {

    private final MemoryProperties properties;
    private final LlmClient llmClient;
    private final MemoryStore memoryStore;
    private final MemoryIndexer indexer;

    /** 已 flush 的 runId 集合（防止同一 run 重复 flush） */
    private final Set<String> flushedRuns = ConcurrentHashMap.newKeySet();

    private static final String FLUSH_PROMPT = """
        请总结当前对话的关键信息，包括：
        1. 用户的核心需求/问题
        2. 已完成的操作和结果
        3. 重要的决策和原因
        4. 需要记住的用户偏好或约束

        用简洁的 Markdown 格式输出，不超过 500 字。
        这份总结将被保存到全局记忆中，供未来的所有对话参考。
        """;

    /**
     * 检查是否需要 flush，如果需要则执行
     *
     * @param runId    当前运行 ID
     * @param messages 当前对话消息列表
     * @return true 如果执行了 flush
     */
    public boolean checkAndFlush(String runId, List<LlmRequest.Message> messages) {
        if (!properties.getFlush().isEnabled()) {
            return false;
        }

        // 已经 flush 过，跳过
        if (flushedRuns.contains(runId)) {
            return false;
        }

        // 估算当前 token 数
        int estimatedTokens = estimateTokens(messages);
        int threshold = properties.getFlush().getTokenThreshold();

        if (estimatedTokens < threshold) {
            return false;
        }

        log.info("Token threshold reached ({} >= {}), triggering memory flush for run {}",
                estimatedTokens, threshold, runId);

        try {
            executeFlush(runId, messages);
            flushedRuns.add(runId);
            return true;
        } catch (Exception e) {
            log.error("Memory flush failed for run {}: {}", runId, e.getMessage());
            return false;
        }
    }

    /**
     * 执行 flush：让 LLM 总结 → 写入全局日记
     */
    private void executeFlush(String runId, List<LlmRequest.Message> messages) throws Exception {
        // 构建总结请求
        List<LlmRequest.Message> summaryMessages = new java.util.ArrayList<>(messages);
        summaryMessages.add(LlmRequest.Message.user(FLUSH_PROMPT));

        LlmRequest request = LlmRequest.builder()
                .messages(summaryMessages)
                .build();

        // 同步调用 LLM 获取总结
        LlmResponse response = llmClient.chat(request);
        String summary = response.getContent();

        if (summary == null || summary.isBlank()) {
            log.warn("Flush summary is empty for run {}", runId);
            return;
        }

        // 写入全局日记文件
        String header = String.format("\n## Session Summary (%s)\n\n",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        memoryStore.appendToDaily(header + summary);

        // 更新索引
        String dailyFile = java.time.LocalDate.now()
                .format(DateTimeFormatter.ISO_LOCAL_DATE) + ".md";
        indexer.indexFile(dailyFile);

        log.info("Memory flush completed for run {}: {} chars written to global memory", runId, summary.length());
    }

    /**
     * 估算消息列表的总 token 数
     */
    private int estimateTokens(List<LlmRequest.Message> messages) {
        return messages.stream()
                .mapToInt(m -> MemoryChunker.estimateTokens(
                        m.getContent() != null ? m.getContent() : ""))
                .sum();
    }

    /**
     * 清理已完成 run 的 flush 标记
     */
    public void clearRun(String runId) {
        flushedRuns.remove(runId);
    }
}
```

**Step: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/memory/flush/PreCompactionFlushHook.java
git commit -m "feat(memory): [P4-11] PreCompactionFlushHook 对话自动落盘到全局记忆"
```

---

### Task P4-12: 集成 AgentRuntime + SystemPromptBuilder

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/runtime/AgentRuntime.java`
- Modify: `src/main/java/com/jaguarliu/ai/runtime/SystemPromptBuilder.java`

**Step 1: AgentRuntime — 添加 flush 检查**

在 `AgentRuntime` 中注入 `PreCompactionFlushHook`：

```java
private final PreCompactionFlushHook flushHook;
```

在 `doExecuteLoop` 的 `while` 循环内部，LLM 调用之前：

```java
// Pre-compaction flush 检查（写入全局记忆）
flushHook.checkAndFlush(context.getRunId(), messages);
```

在 `executeLoop` 的 `finally` 块：

```java
flushHook.clearRun(runId);
```

**Step 2: SystemPromptBuilder — 添加 Memory 段落**

在 `build()` 方法的 FULL 模式中，Safety 段落之后：

```java
// Memory
if (mode == PromptMode.FULL) {
    sb.append(buildMemorySection());
}
```

新增方法：

```java
/**
 * 构建记忆段落
 */
private String buildMemorySection() {
    StringBuilder sb = new StringBuilder();
    sb.append("## Memory\n\n");
    sb.append("You have access to a **global, cross-session** memory system:\n\n");
    sb.append("- `memory_search(query)`: Search all historical memories (preferences, facts, past summaries)\n");
    sb.append("- `memory_get(path)`: Read specific memory files\n");
    sb.append("- `memory_write(content, target)`: Save important information\n");
    sb.append("  - target=\"core\" → MEMORY.md (long-term: preferences, constraints)\n");
    sb.append("  - target=\"daily\" → Today's log (session summaries, work records)\n\n");
    sb.append("**Key point**: Memories are global and cross-session. Information saved today ");
    sb.append("will be searchable in all future conversations. This is a personal assistant, not multi-tenant.\n\n");
    sb.append("**When to use memory:**\n");
    sb.append("- Search for relevant context at conversation start\n");
    sb.append("- Save user preferences/constraints to core memory\n");
    sb.append("- Summarize significant tasks to daily log\n\n");
    return sb.toString();
}
```

**Step: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/runtime/AgentRuntime.java
git add src/main/java/com/jaguarliu/ai/runtime/SystemPromptBuilder.java
git commit -m "feat(memory): [P4-12] 集成 AgentRuntime flush + Memory 系统提示"
```

---

### Task P4-13: memory.status RPC Handler

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/MemoryStatusHandler.java`

```java
package com.jaguarliu.ai.gateway.rpc.handler;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.memory.index.MemoryIndexer;
import com.jaguarliu.ai.memory.store.MemoryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Map;

/**
 * memory.status - 查询全局记忆系统状态
 */
@Component
@RequiredArgsConstructor
public class MemoryStatusHandler implements RpcHandler {

    private final MemoryIndexer indexer;
    private final MemoryStore memoryStore;

    @Override
    public String getMethod() {
        return "memory.status";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        MemoryIndexer.IndexStatus status = indexer.getStatus();

        int fileCount = 0;
        try {
            fileCount = memoryStore.listFiles().size();
        } catch (IOException ignored) {}

        return Mono.just(RpcResponse.success(request.getId(), Map.of(
                "totalChunks", status.totalChunks(),
                "chunksWithEmbedding", status.chunksWithEmbedding(),
                "vectorSearchEnabled", status.vectorSearchEnabled(),
                "embeddingProvider", status.embeddingProvider(),
                "memoryFileCount", fileCount,
                "note", "Memory is global and cross-session"
        )));
    }
}
```

**Step: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/gateway/rpc/handler/MemoryStatusHandler.java
git commit -m "feat(memory): [P4-13] memory.status RPC"
```

---

### Task P4-14: memory.rebuild RPC Handler

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/MemoryRebuildHandler.java`

```java
package com.jaguarliu.ai.gateway.rpc.handler;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.memory.index.MemoryIndexer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * memory.rebuild - 从 Markdown 重建全部全局记忆索引
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryRebuildHandler implements RpcHandler {

    private final MemoryIndexer indexer;

    @Override
    public String getMethod() {
        return "memory.rebuild";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            log.info("Global memory index rebuild requested by connection: {}", connectionId);
            indexer.rebuild();
            MemoryIndexer.IndexStatus status = indexer.getStatus();
            return RpcResponse.success(request.getId(), Map.of(
                    "message", "Global memory index rebuilt successfully",
                    "totalChunks", status.totalChunks(),
                    "chunksWithEmbedding", status.chunksWithEmbedding()
            ));
        });
    }
}
```

**Step: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/gateway/rpc/handler/MemoryRebuildHandler.java
git commit -m "feat(memory): [P4-14] memory.rebuild RPC"
```

---

### Task P4-15: 前端 Memory 状态展示

**Files:**
- Create: `miniclaw-ui/src/composables/useMemory.ts`
- Modify: `miniclaw-ui/src/views/SettingsView.vue`

**目标：**
- 在设置页面添加 Memory 状态面板
- 显示：embedding provider、chunk 总数、记忆文件数
- 强调"全局记忆，跨会话"
- 提供"重建索引"按钮

**Step: Commit**

```bash
git add miniclaw-ui/
git commit -m "feat(memory): [P4-15] 前端 Memory 状态面板"
```

---

## 文件变更汇总

| 操作 | 文件路径 |
|------|----------|
| Create | `src/main/java/com/jaguarliu/ai/memory/MemoryProperties.java` |
| Create | `src/main/java/com/jaguarliu/ai/memory/store/MemoryStore.java` |
| Create | `src/main/java/com/jaguarliu/ai/memory/index/MemoryChunk.java` |
| Create | `src/main/java/com/jaguarliu/ai/memory/index/MemoryChunker.java` |
| Create | `src/main/java/com/jaguarliu/ai/memory/index/MemoryChunkEntity.java` |
| Create | `src/main/java/com/jaguarliu/ai/memory/index/MemoryChunkRepository.java` |
| Create | `src/main/java/com/jaguarliu/ai/memory/index/MemoryIndexer.java` |
| Create | `src/main/java/com/jaguarliu/ai/memory/embedding/EmbeddingClient.java` |
| Create | `src/main/java/com/jaguarliu/ai/memory/embedding/EmbeddingResult.java` |
| Create | `src/main/java/com/jaguarliu/ai/memory/embedding/OpenAiEmbeddingClient.java` |
| Create | `src/main/java/com/jaguarliu/ai/memory/embedding/EmbeddingProviderFactory.java` |
| Create | `src/main/java/com/jaguarliu/ai/memory/search/SearchResult.java` |
| Create | `src/main/java/com/jaguarliu/ai/memory/search/MemorySearchService.java` |
| Create | `src/main/java/com/jaguarliu/ai/memory/flush/PreCompactionFlushHook.java` |
| Create | `src/main/java/com/jaguarliu/ai/tools/builtin/MemorySearchTool.java` |
| Create | `src/main/java/com/jaguarliu/ai/tools/builtin/MemoryGetTool.java` |
| Create | `src/main/java/com/jaguarliu/ai/tools/builtin/MemoryWriteTool.java` |
| Create | `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/MemoryStatusHandler.java` |
| Create | `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/MemoryRebuildHandler.java` |
| Create | `src/main/resources/db/migration/V2__memory_chunks.sql` |
| Modify | `src/main/resources/application.yml` |
| Modify | `src/main/java/com/jaguarliu/ai/runtime/AgentRuntime.java` |
| Modify | `src/main/java/com/jaguarliu/ai/runtime/SystemPromptBuilder.java` |
| Modify | `miniclaw-ui/src/views/SettingsView.vue` |

---

## 验收标准

### 核心功能

1. **全局记忆，跨会话**
   - 会话 A 中 `memory_write("喜欢简洁风格", "core")` → 会话 B 中 `memory_search("风格偏好")` 能检索到
   - `memory_chunks` 表无 `session_id` 字段
   - 所有工具描述强调"全局"、"跨会话"

2. **Retain（写入）— 零 embedding 依赖**
   - `memory_write(content, "core")` → 追加到 `workspace/memory/MEMORY.md`
   - `memory_write(content, "daily")` → 追加到 `workspace/memory/2026-02-05.md`
   - 写入完成后触发索引更新

3. **Recall（检索）— 三级降级**
   - 有 embedding provider 时：向量+FTS 混合
   - 无 embedding provider 时：纯 FTS
   - 返回格式：snippet + 文件路径 + 行号 + score + source

4. **Embedding Provider 自动探测**
   - 启动日志显示 provider 状态
   - `memory.status` RPC 返回当前配置

5. **索引可重建**
   - `memory.rebuild` RPC 从 Markdown 重建
   - 体现"Markdown 是真相源"

6. **Pre-compaction Flush**
   - 长对话自动总结 → 写入全局日记
   - 后续任何会话都能检索到

### 配置零依赖

7. **无 embedding 时正常启动**
   - 向量检索自动禁用，FTS 兜底
   - 所有工具正常工作

---

## 教学知识点

| Task | 核心知识点 |
|------|-----------|
| P4-01 | 配置驱动设计、嵌套 @ConfigurationProperties |
| P4-02 | Markdown-as-source-of-truth、全局存储设计 |
| P4-03 | Chunking 策略、token 估算 |
| P4-04 | Adapter 模式、工厂模式、自动探测 |
| P4-05 | pgvector、tsvector、数据库触发器、无 session_id 的全局设计 |
| P4-06 | 派生索引、可选组件、优雅降级 |
| P4-07 | 混合检索、结果合并、全局检索 |
| P4-08~10 | 全局记忆工具设计、跨会话语义 |
| P4-11 | Compaction 策略、全局自动落盘 |
| P4-12 | 系统集成、模块化 Prompt |
