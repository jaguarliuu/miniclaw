package com.jaguarliu.ai.memory;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Memory 子系统配置
 *
 * 设计原则：记忆是全局的、跨会话的。
 * 这是个人助手，不是多租户系统。
 */
@Data
@Configuration
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
        /** 触发 flush 的 token 阈值（估算值，默认 80000 适合 128k+ context 模型） */
        private int tokenThreshold = 80000;
    }
}
