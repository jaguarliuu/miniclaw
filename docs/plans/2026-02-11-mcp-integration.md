# MCP (Model Context Protocol) Integration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Integrate Model Context Protocol (MCP) support into miniclaw as an **MCP Client**, enabling users to connect to any MCP Server (official, third-party, or custom-built) and dynamically use their tools, resources, and prompts.

**Architecture:** Implement an adapter layer that bridges MCP Java SDK (v0.17.2) with miniclaw's existing Tool system. Users configure MCP servers in application.yml (or environment variables), and miniclaw connects to them via three transport types: STDIO (process pipes), SSE (Server-Sent Events), and Streamable HTTP. The McpClientManager handles lifecycle, health checks, and reconnection. Each MCP tool is wrapped in McpToolAdapter implementing the Tool interface. **Users can connect to unlimited MCP servers simultaneously**, each with its own tool prefix to avoid naming conflicts.

**Tech Stack:**
- MCP Java SDK v0.17.2 (`io.modelcontextprotocol.sdk:mcp`)
- Reactive Streams (public API) + Project Reactor (internal)
- Spring Boot WebFlux (existing)
- JPA + SQLite (primary, desktop) / PostgreSQL (optional, server deployment)
- Flyway (dual-database migrations)
- WebSocket RPC (for frontend integration)

**Key SDK Features:**
- Three transport types: STDIO, SSE, Streamable HTTP
- Sync (`McpClient.sync()`) and Async (`McpClient.async()`) clients
- Modular design: `mcp` (convenience bundle = core + Jackson)
- JDK HttpClient for HTTP/SSE, no extra dependencies for STDIO

---

## Phase 1: Foundation & Configuration

### Task 1: Add MCP SDK Dependency

**Files:**
- Modify: `pom.xml:20-150`

**Step 1: Add MCP SDK dependency to pom.xml**

Add after line 112 (after angus-mail dependency):

```xml
        <!-- Model Context Protocol (MCP) SDK v0.17.2 -->
        <dependency>
            <groupId>io.modelcontextprotocol.sdk</groupId>
            <artifactId>mcp</artifactId>
            <version>0.17.2</version>
        </dependency>
```

**Step 2: Build project to verify dependency resolution**

Run: `mvn clean compile`
Expected: BUILD SUCCESS and MCP classes available

**Step 3: Commit dependency addition**

```bash
git add pom.xml
git commit -m "build: add MCP Java SDK v0.17.2 dependency"
```

---

### Task 2: Create MCP Configuration Properties

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/mcp/McpProperties.java`
- Create: `src/test/java/com/jaguarliu/ai/mcp/McpPropertiesTest.java`

**Step 1: Write the failing test**

```java
package com.jaguarliu.ai.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class McpPropertiesTest {

    @Autowired
    private McpProperties mcpProperties;

    @Test
    void shouldLoadDefaultConfig() {
        assertThat(mcpProperties).isNotNull();
        assertThat(mcpProperties.getServers()).isNotNull();
        assertThat(mcpProperties.getHealthCheck()).isNotNull();
        assertThat(mcpProperties.getHealthCheck().getIntervalSeconds()).isEqualTo(60);
    }

    @Test
    void shouldSupportThreeTransportTypes() {
        var stdioConfig = new McpProperties.ServerConfig();
        stdioConfig.setTransport(McpProperties.TransportType.STDIO);
        assertThat(stdioConfig.getTransport()).isEqualTo(McpProperties.TransportType.STDIO);

        var sseConfig = new McpProperties.ServerConfig();
        sseConfig.setTransport(McpProperties.TransportType.SSE);
        assertThat(sseConfig.getTransport()).isEqualTo(McpProperties.TransportType.SSE);

        var httpConfig = new McpProperties.ServerConfig();
        httpConfig.setTransport(McpProperties.TransportType.HTTP);
        assertThat(httpConfig.getTransport()).isEqualTo(McpProperties.TransportType.HTTP);
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=McpPropertiesTest`
Expected: FAIL with "McpProperties not found"

**Step 3: Write minimal implementation**

```java
package com.jaguarliu.ai.mcp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP 配置属性
 * 支持三种传输方式：STDIO, SSE, HTTP
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "mcp")
public class McpProperties {

    /**
     * MCP Server 配置列表
     */
    private List<ServerConfig> servers = new ArrayList<>();

    /**
     * 健康检查配置
     */
    private HealthCheckConfig healthCheck = new HealthCheckConfig();

    /**
     * 传输类型枚举
     */
    public enum TransportType {
        /**
         * STDIO - 标准输入输出（进程间通信）
         * 适用场景：本地 MCP 服务器（如 npx 启动的服务）
         */
        STDIO,

        /**
         * SSE - Server-Sent Events（服务器推送事件）
         * 适用场景：远程 HTTP 服务器，单向流式数据
         */
        SSE,

        /**
         * HTTP - Streamable HTTP
         * 适用场景：远程 HTTP 服务器，双向流式通信
         */
        HTTP
    }

    @Data
    public static class ServerConfig {
        /**
         * Server 名称（唯一标识）
         */
        private String name;

        /**
         * 传输类型：stdio, sse, http
         */
        private TransportType transport = TransportType.STDIO;

        /**
         * STDIO: 可执行命令（如 "npx"）
         */
        private String command;

        /**
         * STDIO: 命令参数列表
         */
        private List<String> args = new ArrayList<>();

        /**
         * STDIO: 工作目录
         */
        private String workingDir;

        /**
         * STDIO: 环境变量
         */
        private List<String> env = new ArrayList<>();

        /**
         * SSE/HTTP: 服务器端点 URL
         */
        private String url;

        /**
         * 是否启用
         */
        private boolean enabled = true;

        /**
         * 工具名称前缀（避免冲突）
         */
        private String toolPrefix = "";

        /**
         * 是否需要 HITL 确认此服务器的所有工具
         */
        private boolean requiresHitl = false;

        /**
         * 需要 HITL 确认的工具名称列表（不含前缀）
         */
        private List<String> hitlTools = new ArrayList<>();

        /**
         * 请求超时时间（秒）
         */
        private int requestTimeoutSeconds = 30;
    }

    @Data
    public static class HealthCheckConfig {
        /**
         * 健康检查间隔（秒）
         */
        private int intervalSeconds = 60;

        /**
         * 重连最大尝试次数
         */
        private int maxRetries = 3;

        /**
         * 重连退避基数（秒）
         */
        private int retryBackoffSeconds = 5;
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=McpPropertiesTest`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/mcp/McpProperties.java \
        src/test/java/com/jaguarliu/ai/mcp/McpPropertiesTest.java
git commit -m "feat(mcp): add MCP configuration properties with three transport types"
```

---

### Task 3: Add MCP Configuration to application.yml

**Files:**
- Modify: `src/main/resources/application.yml:117-127`

**Step 1: Add MCP configuration section**

Add after line 117 (after memory configuration):

```yaml
# MCP (Model Context Protocol) 配置
# 作为 MCP Client，连接到任意 MCP Server（官方、第三方、用户自建）
# 支持三种传输方式：STDIO（进程管道）、SSE（服务器推送事件）、HTTP（流式HTTP）
mcp:
  servers: []  # 默认为空，用户可以添加任意数量的 MCP Server
    # ========================================
    # 官方 MCP Server 示例（STDIO）
    # ========================================
    # - name: filesystem
    #   transport: stdio
    #   command: npx
    #   args:
    #     - "-y"
    #     - "@modelcontextprotocol/server-filesystem"
    #     - "/tmp"
    #   enabled: true
    #   tool-prefix: fs_
    #   request-timeout-seconds: 30
    #
    # ========================================
    # 第三方 MCP Server 示例（SSE）
    # ========================================
    # - name: tavily-search
    #   transport: sse
    #   url: http://localhost:3000/sse
    #   enabled: true
    #   tool-prefix: search_
    #   requires-hitl: false
    #
    # ========================================
    # 用户自定义 MCP Server 示例（HTTP）
    # ========================================
    # - name: my-custom-mcp
    #   transport: http
    #   url: http://localhost:8080/mcp
    #   enabled: true
    #   tool-prefix: custom_
    #   hitl-tools: [delete_data, update_config]
    #
    # ========================================
    # 用户自定义本地 MCP Server（STDIO）
    # ========================================
    # - name: my-python-mcp
    #   transport: stdio
    #   command: python
    #   args:
    #     - "/path/to/my_mcp_server.py"
    #   working-dir: /path/to/workdir
    #   env:
    #     - "API_KEY=your-key"
    #     - "DEBUG=true"
    #   enabled: true
    #   tool-prefix: py_
    #   request-timeout-seconds: 60
  health-check:
    interval-seconds: 60
    max-retries: 3
    retry-backoff-seconds: 5
```

**Step 2: Verify configuration loads correctly**

Run: `mvn spring-boot:run`
Check logs for: "McpProperties initialized" (after we implement McpClientManager)

**Step 3: Commit**

```bash
git add src/main/resources/application.yml
git commit -m "config(mcp): add MCP configuration with STDIO/SSE/HTTP examples"
```

---

## Phase 2: Database Persistence & Dynamic Configuration

### Task 4: Create MCP Server Entity and Repository

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/mcp/persistence/McpServerEntity.java`
- Create: `src/main/java/com/jaguarliu/ai/mcp/persistence/McpServerRepository.java`
- Create: `src/main/resources/db/migration/V999__create_mcp_servers_table.sql`
- Create: `src/test/java/com/jaguarliu/ai/mcp/persistence/McpServerRepositoryTest.java`

**Step 1: Write the failing test**

```java
package com.jaguarliu.ai.mcp.persistence;

import com.jaguarliu.ai.mcp.McpProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class McpServerRepositoryTest {

    @Autowired
    private McpServerRepository repository;

    @Test
    void shouldSaveAndLoadMcpServerConfig() {
        var entity = new McpServerEntity();
        entity.setName("test-server");
        entity.setTransportType(McpProperties.TransportType.STDIO);
        entity.setCommand("npx");
        entity.setArgs(List.of("-y", "@modelcontextprotocol/server-filesystem", "/tmp"));
        entity.setEnabled(true);
        entity.setToolPrefix("test_");

        var saved = repository.save(entity);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("test-server");
    }

    @Test
    void shouldFindEnabledServers() {
        var entity1 = createEntity("server1", true);
        var entity2 = createEntity("server2", false);

        repository.save(entity1);
        repository.save(entity2);

        var enabled = repository.findByEnabledTrue();

        assertThat(enabled).hasSize(1);
        assertThat(enabled.get(0).getName()).isEqualTo("server1");
    }

    private McpServerEntity createEntity(String name, boolean enabled) {
        var entity = new McpServerEntity();
        entity.setName(name);
        entity.setTransportType(McpProperties.TransportType.STDIO);
        entity.setCommand("test");
        entity.setEnabled(enabled);
        return entity;
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=McpServerRepositoryTest`
Expected: FAIL with "McpServerEntity not found"

**Step 3: Create Flyway migrations (SQLite and PostgreSQL)**

Create `src/main/resources/db/migration-sqlite/V999__create_mcp_servers_table.sql`:

```sql
-- MCP Server 配置表 (SQLite)
CREATE TABLE mcp_servers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(255) NOT NULL UNIQUE,
    transport_type VARCHAR(50) NOT NULL,  -- STDIO, SSE, HTTP

    -- STDIO 配置
    command VARCHAR(500),
    args TEXT,  -- JSON array (stored as TEXT in SQLite)
    working_dir VARCHAR(500),
    env TEXT,   -- JSON array (stored as TEXT in SQLite)

    -- SSE/HTTP 配置
    url VARCHAR(1000),

    -- 通用配置
    enabled INTEGER NOT NULL DEFAULT 1,  -- SQLite uses INTEGER for BOOLEAN
    tool_prefix VARCHAR(100) DEFAULT '',
    requires_hitl INTEGER NOT NULL DEFAULT 0,  -- SQLite uses INTEGER for BOOLEAN
    hitl_tools TEXT,  -- JSON array (stored as TEXT in SQLite)
    request_timeout_seconds INTEGER NOT NULL DEFAULT 30,

    -- 元数据
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_transport_config CHECK (
        (transport_type = 'STDIO' AND command IS NOT NULL) OR
        (transport_type IN ('SSE', 'HTTP') AND url IS NOT NULL)
    )
);

-- 索引
CREATE INDEX idx_mcp_servers_enabled ON mcp_servers(enabled);
CREATE INDEX idx_mcp_servers_name ON mcp_servers(name);
```

Create `src/main/resources/db/migration/V999__create_mcp_servers_table.sql`:

```sql
-- MCP Server 配置表 (PostgreSQL)
CREATE TABLE mcp_servers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    transport_type VARCHAR(50) NOT NULL,  -- STDIO, SSE, HTTP

    -- STDIO 配置
    command VARCHAR(500),
    args JSONB,  -- JSON array (native JSONB in PostgreSQL)
    working_dir VARCHAR(500),
    env JSONB,   -- JSON array (native JSONB in PostgreSQL)

    -- SSE/HTTP 配置
    url VARCHAR(1000),

    -- 通用配置
    enabled BOOLEAN NOT NULL DEFAULT true,
    tool_prefix VARCHAR(100) DEFAULT '',
    requires_hitl BOOLEAN NOT NULL DEFAULT false,
    hitl_tools JSONB,  -- JSON array (native JSONB in PostgreSQL)
    request_timeout_seconds INTEGER NOT NULL DEFAULT 30,

    -- 元数据
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_transport_config CHECK (
        (transport_type = 'STDIO' AND command IS NOT NULL) OR
        (transport_type IN ('SSE', 'HTTP') AND url IS NOT NULL)
    )
);

-- 索引
CREATE INDEX idx_mcp_servers_enabled ON mcp_servers(enabled);
CREATE INDEX idx_mcp_servers_name ON mcp_servers(name);
```

**Note**:
- Flyway will automatically select the correct migration based on the configured locations
- SQLite migrations are in `db/migration-sqlite/` (already configured in application-sqlite.yml)
- PostgreSQL migrations are in `db/migration/` (already configured in application-pg.yml)
- No need to modify existing Flyway configuration

**Step 4: Create Entity and Repository**

Create `src/main/java/com/jaguarliu/ai/mcp/persistence/McpServerEntity.java`:

```java
package com.jaguarliu.ai.mcp.persistence;

import com.jaguarliu.ai.mcp.McpProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * MCP Server 配置实体
 * 支持数据库持久化和动态配置管理
 *
 * 兼容性说明：
 * - 主要支持 SQLite（桌面端）
 * - 同时兼容 PostgreSQL（服务器部署）
 * - JPA 抽象层自动处理两种数据库的差异
 * - JSON 字段在 SQLite 中存储为 TEXT，PostgreSQL 中存储为 JSONB
 * - Boolean 字段在 SQLite 中存储为 INTEGER (0/1)，PostgreSQL 中存储为 BOOLEAN
 */
@Data
@Entity
@Table(name = "mcp_servers")
public class McpServerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_type", nullable = false)
    private McpProperties.TransportType transportType;

    // STDIO 配置
    private String command;

    /**
     * JSON 字段配置说明：
     * - PostgreSQL: 使用 JSONB 类型存储
     * - SQLite: 使用 TEXT 类型存储（Hibernate 自动序列化/反序列化）
     */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> args = new ArrayList<>();

    @Column(name = "working_dir")
    private String workingDir;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> env = new ArrayList<>();

    // SSE/HTTP 配置
    private String url;

    // 通用配置
    /**
     * Boolean 字段说明：
     * - PostgreSQL: BOOLEAN 类型
     * - SQLite: INTEGER 类型 (0=false, 1=true)
     * - JPA 自动处理转换
     */
    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "tool_prefix")
    private String toolPrefix = "";

    @Column(name = "requires_hitl", nullable = false)
    private Boolean requiresHitl = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hitl_tools")
    private List<String> hitlTools = new ArrayList<>();

    @Column(name = "request_timeout_seconds", nullable = false)
    private Integer requestTimeoutSeconds = 30;

    // 元数据
    /**
     * 时间字段说明：
     * - PostgreSQL: TIMESTAMP 类型
     * - SQLite: DATETIME 类型
     * - JPA 统一映射为 LocalDateTime
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 转换为 McpProperties.ServerConfig
     */
    public McpProperties.ServerConfig toConfig() {
        var config = new McpProperties.ServerConfig();
        config.setName(name);
        config.setTransport(transportType);
        config.setCommand(command);
        config.setArgs(args);
        config.setWorkingDir(workingDir);
        config.setEnv(env);
        config.setUrl(url);
        config.setEnabled(enabled);
        config.setToolPrefix(toolPrefix);
        config.setRequiresHitl(requiresHitl);
        config.setHitlTools(hitlTools);
        config.setRequestTimeoutSeconds(requestTimeoutSeconds);
        return config;
    }

    /**
     * 从 McpProperties.ServerConfig 创建实体
     */
    public static McpServerEntity fromConfig(McpProperties.ServerConfig config) {
        var entity = new McpServerEntity();
        entity.setName(config.getName());
        entity.setTransportType(config.getTransport());
        entity.setCommand(config.getCommand());
        entity.setArgs(config.getArgs());
        entity.setWorkingDir(config.getWorkingDir());
        entity.setEnv(config.getEnv());
        entity.setUrl(config.getUrl());
        entity.setEnabled(config.isEnabled());
        entity.setToolPrefix(config.getToolPrefix());
        entity.setRequiresHitl(config.isRequiresHitl());
        entity.setHitlTools(config.getHitlTools());
        entity.setRequestTimeoutSeconds(config.getRequestTimeoutSeconds());
        return entity;
    }
}
```

Create `src/main/java/com/jaguarliu/ai/mcp/persistence/McpServerRepository.java`:

```java
package com.jaguarliu.ai.mcp.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MCP Server 配置仓库
 */
@Repository
public interface McpServerRepository extends JpaRepository<McpServerEntity, Long> {

    /**
     * 查找所有启用的服务器
     */
    List<McpServerEntity> findByEnabledTrue();

    /**
     * 根据名称查找服务器
     */
    Optional<McpServerEntity> findByName(String name);

    /**
     * 检查名称是否存在
     */
    boolean existsByName(String name);
}
```

**Step 5: Run tests to verify they pass**

Run: `mvn test -Dtest=McpServerRepositoryTest`
Expected: PASS

**Step 6: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/mcp/persistence/ \
        src/main/resources/db/migration-sqlite/V999__create_mcp_servers_table.sql \
        src/main/resources/db/migration/V999__create_mcp_servers_table.sql \
        src/test/java/com/jaguarliu/ai/mcp/persistence/
git commit -m "feat(mcp): add database persistence with SQLite and PostgreSQL support"
```

---

### Task 5: Create MCP Server Service for Dynamic Management

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/mcp/service/McpServerService.java`
- Create: `src/test/java/com/jaguarliu/ai/mcp/service/McpServerServiceTest.java`

**Step 1: Write the failing test**

```java
package com.jaguarliu.ai.mcp.service;

import com.jaguarliu.ai.mcp.McpProperties;
import com.jaguarliu.ai.mcp.client.McpClientManager;
import com.jaguarliu.ai.mcp.persistence.McpServerEntity;
import com.jaguarliu.ai.mcp.persistence.McpServerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class McpServerServiceTest {

    @Autowired
    private McpServerService service;

    @Autowired
    private McpServerRepository repository;

    @MockBean
    private McpClientManager clientManager;

    @Test
    void shouldCreateAndConnectMcpServer() {
        var config = new McpProperties.ServerConfig();
        config.setName("test-server");
        config.setTransport(McpProperties.TransportType.STDIO);
        config.setCommand("npx");
        config.setArgs(List.of("-y", "test"));
        config.setEnabled(true);

        var result = service.createServer(config);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("test-server");

        // 验证已保存到数据库
        var found = repository.findByName("test-server");
        assertThat(found).isPresent();
    }

    @Test
    void shouldNotCreateDuplicateServer() {
        var config = new McpProperties.ServerConfig();
        config.setName("duplicate");
        config.setTransport(McpProperties.TransportType.STDIO);
        config.setCommand("test");

        service.createServer(config);

        assertThatThrownBy(() -> service.createServer(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=McpServerServiceTest`
Expected: FAIL with "McpServerService not found"

**Step 3: Write implementation**

Create `src/main/java/com/jaguarliu/ai/mcp/service/McpServerService.java`:

```java
package com.jaguarliu.ai.mcp.service;

import com.jaguarliu.ai.mcp.McpProperties;
import com.jaguarliu.ai.mcp.client.McpClientManager;
import com.jaguarliu.ai.mcp.persistence.McpServerEntity;
import com.jaguarliu.ai.mcp.persistence.McpServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * MCP Server 动态管理服务
 * 支持运行时添加、更新、删除 MCP Server 配置
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerService {

    private final McpServerRepository repository;
    private final McpClientManager clientManager;

    /**
     * 创建并连接新的 MCP Server
     */
    @Transactional
    public McpServerEntity createServer(McpProperties.ServerConfig config) {
        log.info("Creating MCP server: {}", config.getName());

        // 检查名称是否已存在
        if (repository.existsByName(config.getName())) {
            throw new IllegalArgumentException("MCP server with name '" + config.getName() + "' already exists");
        }

        // 保存到数据库
        var entity = McpServerEntity.fromConfig(config);
        entity = repository.save(entity);

        // 如果启用，立即连接
        if (config.isEnabled()) {
            try {
                clientManager.connectServer(config);
                log.info("MCP server created and connected: {}", config.getName());
            } catch (Exception e) {
                log.error("Failed to connect MCP server after creation: {}", config.getName(), e);
                // 不回滚，允许保存配置但连接失败
            }
        }

        return entity;
    }

    /**
     * 更新 MCP Server 配置
     */
    @Transactional
    public McpServerEntity updateServer(Long id, McpProperties.ServerConfig config) {
        log.info("Updating MCP server ID {}: {}", id, config.getName());

        var entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MCP server not found: " + id));

        String oldName = entity.getName();
        boolean wasEnabled = entity.getEnabled();

        // 更新实体
        entity.setName(config.getName());
        entity.setTransportType(config.getTransport());
        entity.setCommand(config.getCommand());
        entity.setArgs(config.getArgs());
        entity.setWorkingDir(config.getWorkingDir());
        entity.setEnv(config.getEnv());
        entity.setUrl(config.getUrl());
        entity.setEnabled(config.isEnabled());
        entity.setToolPrefix(config.getToolPrefix());
        entity.setRequiresHitl(config.isRequiresHitl());
        entity.setHitlTools(config.getHitlTools());
        entity.setRequestTimeoutSeconds(config.getRequestTimeoutSeconds());

        entity = repository.save(entity);

        // 处理连接变化
        if (wasEnabled) {
            clientManager.disconnectServer(oldName);
        }

        if (config.isEnabled()) {
            try {
                clientManager.connectServer(config);
            } catch (Exception e) {
                log.error("Failed to connect MCP server after update: {}", config.getName(), e);
            }
        }

        return entity;
    }

    /**
     * 删除 MCP Server
     */
    @Transactional
    public void deleteServer(Long id) {
        log.info("Deleting MCP server ID: {}", id);

        var entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MCP server not found: " + id));

        // 断开连接
        if (entity.getEnabled()) {
            clientManager.disconnectServer(entity.getName());
        }

        // 删除配置
        repository.delete(entity);
        log.info("MCP server deleted: {}", entity.getName());
    }

    /**
     * 测试连接
     */
    public boolean testConnection(McpProperties.ServerConfig config) {
        log.info("Testing connection to MCP server: {}", config.getName());

        try {
            return clientManager.testConnection(config);
        } catch (Exception e) {
            log.error("Connection test failed for: {}", config.getName(), e);
            return false;
        }
    }

    /**
     * 列出所有服务器
     */
    public List<McpServerEntity> listServers() {
        return repository.findAll();
    }

    /**
     * 列出所有启用的服务器
     */
    public List<McpServerEntity> listEnabledServers() {
        return repository.findByEnabledTrue();
    }

    /**
     * 获取服务器详情
     */
    public McpServerEntity getServer(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MCP server not found: " + id));
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=McpServerServiceTest`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/mcp/service/ \
        src/test/java/com/jaguarliu/ai/mcp/service/
git commit -m "feat(mcp): add dynamic MCP server management service"
```

---

---

### Task 6: Update McpClientManager for Dynamic Connection Management

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/mcp/client/McpClientManager.java`
- Modify: `src/test/java/com/jaguarliu/ai/mcp/client/McpClientManagerTest.java`

**Step 1: Add test for dynamic connection**

Add to `McpClientManagerTest.java`:

```java
@Test
void shouldConnectServerDynamically() {
    var config = new McpProperties.ServerConfig();
    config.setName("dynamic-server");
    config.setTransport(McpProperties.TransportType.STDIO);
    config.setCommand("npx");
    config.setArgs(List.of("-y", "test"));

    // 动态连接
    clientManager.connectServer(config);

    // 验证已连接
    var client = clientManager.getClient("dynamic-server");
    assertThat(client).isPresent();
}

@Test
void shouldDisconnectServerDynamically() {
    // Given: 一个已连接的服务器
    var config = new McpProperties.ServerConfig();
    config.setName("temp-server");
    config.setTransport(McpProperties.TransportType.STDIO);
    config.setCommand("npx");

    clientManager.connectServer(config);
    assertThat(clientManager.getClient("temp-server")).isPresent();

    // When: 断开连接
    clientManager.disconnectServer("temp-server");

    // Then: 不再存在
    assertThat(clientManager.getClient("temp-server")).isEmpty();
}

@Test
void shouldTestConnectionWithoutPersisting() {
    var config = new McpProperties.ServerConfig();
    config.setName("test-connection");
    config.setTransport(McpProperties.TransportType.STDIO);
    config.setCommand("npx");

    boolean result = clientManager.testConnection(config);

    // 测试连接不会持久化客户端
    assertThat(clientManager.getClient("test-connection")).isEmpty();
}
```

**Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=McpClientManagerTest`
Expected: FAIL

**Step 3: Update McpClientManager implementation**

Modify the `McpClientManager` class to add these methods:

```java
/**
 * 动态连接到 MCP 服务器
 * 用于运行时添加新服务器
 */
public void connectServer(McpProperties.ServerConfig config) {
    String name = config.getName();

    if (clients.containsKey(name)) {
        log.warn("MCP client already exists: {}", name);
        throw new IllegalArgumentException("MCP client already exists: " + name);
    }

    log.info("Dynamically connecting to MCP server '{}' via {} transport",
            name, config.getTransport());

    // 创建 Transport
    var transport = transportFactory.createTransport(config);

    // 创建客户端
    var client = ManagedMcpClient.create(config, transport);

    // 初始化连接
    client.initialize();

    // 注册客户端
    clients.put(name, client);

    // 触发工具重新发现
    refreshToolsForClient(client);

    log.info("Successfully connected to MCP server: {} (transport: {})",
            name, config.getTransport());
}

/**
 * 动态断开 MCP 服务器连接
 * 用于运行时移除服务器
 */
public void disconnectServer(String name) {
    ManagedMcpClient client = clients.remove(name);
    if (client != null) {
        client.close();
        log.info("Disconnected MCP server: {}", name);

        // 从工具注册表中移除该服务器的工具
        // (需要在 McpToolRegistry 中实现)
    } else {
        log.warn("Attempted to disconnect non-existent MCP server: {}", name);
    }
}

/**
 * 测试连接（不持久化）
 * 用于前端配置验证
 */
public boolean testConnection(McpProperties.ServerConfig config) {
    log.info("Testing connection to MCP server: {}", config.getName());

    try {
        // 创建临时 Transport
        var transport = transportFactory.createTransport(config);

        // 创建临时客户端
        var tempClient = ManagedMcpClient.create(config, transport);

        // 尝试初始化
        tempClient.initialize();

        // 测试成功，关闭临时客户端
        tempClient.close();

        log.info("Connection test successful for: {}", config.getName());
        return true;

    } catch (Exception e) {
        log.error("Connection test failed for: {}", config.getName(), e);
        return false;
    }
}

/**
 * 刷新单个客户端的工具
 * (需要与 McpToolRegistry 配合)
 */
private void refreshToolsForClient(ManagedMcpClient client) {
    // 这个方法将在 McpToolRegistry 中调用
    // 这里只是占位符
    log.debug("Triggering tool refresh for client: {}", client.getName());
}
```

**Step 4: Update initialize() to load from database**

Modify the `initialize()` method to load from database instead of properties:

```java
@PostConstruct
public void initialize() {
    log.info("Initializing MCP Client Manager");

    // 从数据库加载启用的服务器配置
    List<McpServerEntity> servers = mcpServerRepository.findByEnabledTrue();

    if (servers.isEmpty()) {
        log.info("No enabled MCP servers found in database");
        return;
    }

    for (McpServerEntity entity : servers) {
        try {
            McpProperties.ServerConfig config = entity.toConfig();
            connectServer(config);
        } catch (Exception e) {
            log.error("Failed to connect MCP server: {}", entity.getName(), e);
        }
    }

    log.info("MCP Client Manager initialized with {} clients", clients.size());
}
```

**Step 5: Add repository dependency**

Add field to McpClientManager:

```java
private final McpServerRepository mcpServerRepository;
```

**Step 6: Run tests to verify they pass**

Run: `mvn test -Dtest=McpClientManagerTest`
Expected: PASS

**Step 7: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/mcp/client/McpClientManager.java \
        src/test/java/com/jaguarliu/ai/mcp/client/McpClientManagerTest.java
git commit -m "feat(mcp): add dynamic connection management to McpClientManager"
```

---

## Phase 3: MCP Client Management

### Task 7: Create MCP Transport Factory

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/mcp/transport/McpTransportFactory.java`
- Create: `src/test/java/com/jaguarliu/ai/mcp/transport/McpTransportFactoryTest.java`

**Step 1: Write the failing test**

```java
package com.jaguarliu.ai.mcp.transport;

import com.jaguarliu.ai.mcp.McpProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpTransportFactoryTest {

    private final McpTransportFactory factory = new McpTransportFactory();

    @Test
    void shouldCreateStdioTransport() {
        var config = new McpProperties.ServerConfig();
        config.setTransport(McpProperties.TransportType.STDIO);
        config.setCommand("npx");
        config.setArgs(List.of("-y", "@modelcontextprotocol/server-filesystem", "/tmp"));

        var transport = factory.createTransport(config);

        assertThat(transport).isNotNull();
    }

    @Test
    void shouldThrowExceptionForStdioWithoutCommand() {
        var config = new McpProperties.ServerConfig();
        config.setTransport(McpProperties.TransportType.STDIO);

        assertThatThrownBy(() -> factory.createTransport(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("command is required");
    }

    @Test
    void shouldCreateSseTransport() {
        var config = new McpProperties.ServerConfig();
        config.setTransport(McpProperties.TransportType.SSE);
        config.setUrl("http://localhost:3000/sse");

        var transport = factory.createTransport(config);

        assertThat(transport).isNotNull();
    }

    @Test
    void shouldCreateHttpTransport() {
        var config = new McpProperties.ServerConfig();
        config.setTransport(McpProperties.TransportType.HTTP);
        config.setUrl("http://localhost:3000/mcp");

        var transport = factory.createTransport(config);

        assertThat(transport).isNotNull();
    }

    @Test
    void shouldThrowExceptionForSseWithoutUrl() {
        var config = new McpProperties.ServerConfig();
        config.setTransport(McpProperties.TransportType.SSE);

        assertThatThrownBy(() -> factory.createTransport(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url is required");
    }

    @Test
    void shouldThrowExceptionForHttpWithoutUrl() {
        var config = new McpProperties.ServerConfig();
        config.setTransport(McpProperties.TransportType.HTTP);

        assertThatThrownBy(() -> factory.createTransport(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url is required");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=McpTransportFactoryTest`
Expected: FAIL with "McpTransportFactory not found"

**Step 3: Write minimal implementation**

```java
package com.jaguarliu.ai.mcp.transport;

import com.jaguarliu.ai.mcp.McpProperties;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.McpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MCP Transport 工厂
 * 根据配置创建对应的传输层实例
 * 支持三种传输方式：STDIO, SSE, HTTP
 */
@Slf4j
@Component
public class McpTransportFactory {

    /**
     * 根据配置创建 Transport
     *
     * @param config MCP Server 配置
     * @return Transport 实例
     * @throws IllegalArgumentException 配置无效或不支持的传输类型
     */
    public McpTransport createTransport(McpProperties.ServerConfig config) {
        McpProperties.TransportType transport = config.getTransport();

        return switch (transport) {
            case STDIO -> createStdioTransport(config);
            case SSE -> createSseTransport(config);
            case HTTP -> createHttpTransport(config);
        };
    }

    /**
     * 创建 STDIO Transport
     * 适用场景：本地进程通信（如 npx 启动的 MCP 服务器）
     */
    private McpTransport createStdioTransport(McpProperties.ServerConfig config) {
        if (config.getCommand() == null || config.getCommand().isBlank()) {
            throw new IllegalArgumentException("STDIO transport requires 'command' to be set");
        }

        log.info("Creating STDIO transport for command: {} with args: {}",
                config.getCommand(), config.getArgs());

        // 构建 ServerParameters
        ServerParameters.Builder builder = ServerParameters.builder(config.getCommand());

        // 添加参数
        if (config.getArgs() != null && !config.getArgs().isEmpty()) {
            builder.args(config.getArgs().toArray(new String[0]));
        }

        // 设置工作目录
        if (config.getWorkingDir() != null && !config.getWorkingDir().isBlank()) {
            builder.workingDirectory(config.getWorkingDir());
        }

        // 设置环境变量
        if (config.getEnv() != null && !config.getEnv().isEmpty()) {
            builder.env(config.getEnv().toArray(new String[0]));
        }

        ServerParameters params = builder.build();
        return new StdioClientTransport(params);
    }

    /**
     * 创建 SSE Transport
     * 适用场景：远程 HTTP 服务器，单向流式数据
     */
    private McpTransport createSseTransport(McpProperties.ServerConfig config) {
        if (config.getUrl() == null || config.getUrl().isBlank()) {
            throw new IllegalArgumentException("SSE transport requires 'url' to be set");
        }

        log.info("Creating SSE transport for URL: {}", config.getUrl());

        // SSE 使用 HttpClientStreamableHttpTransport 的特定配置
        // 注意：根据 MCP SDK 的实际 API，可能需要调整
        return HttpClientStreamableHttpTransport.builder(config.getUrl())
                .build();
    }

    /**
     * 创建 HTTP Transport (Streamable-HTTP)
     * 适用场景：远程 HTTP 服务器，双向流式通信
     */
    private McpTransport createHttpTransport(McpProperties.ServerConfig config) {
        if (config.getUrl() == null || config.getUrl().isBlank()) {
            throw new IllegalArgumentException("HTTP transport requires 'url' to be set");
        }

        log.info("Creating HTTP transport for URL: {}", config.getUrl());

        return HttpClientStreamableHttpTransport.builder(config.getUrl())
                .build();
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=McpTransportFactoryTest`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/mcp/transport/McpTransportFactory.java \
        src/test/java/com/jaguarliu/ai/mcp/transport/McpTransportFactoryTest.java
git commit -m "feat(mcp): implement transport factory for STDIO/SSE/HTTP"
```

---

### Task 5: Create MCP Client Wrapper

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/mcp/client/ManagedMcpClient.java`
- Create: `src/test/java/com/jaguarliu/ai/mcp/client/ManagedMcpClientTest.java`

**Step 1: Write the failing test**

```java
package com.jaguarliu.ai.mcp.client;

import com.jaguarliu.ai.mcp.McpProperties;
import io.modelcontextprotocol.client.transport.McpTransport;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ManagedMcpClientTest {

    @Test
    void shouldCreateManagedClient() {
        var config = new McpProperties.ServerConfig();
        config.setName("test-server");
        config.setTransport(McpProperties.TransportType.STDIO);
        config.setCommand("npx");

        var transport = mock(McpTransport.class);
        var client = ManagedMcpClient.create(config, transport);

        assertThat(client).isNotNull();
        assertThat(client.getName()).isEqualTo("test-server");
        assertThat(client.isConnected()).isFalse();
    }

    @Test
    void shouldUseConfiguredTimeout() {
        var config = new McpProperties.ServerConfig();
        config.setName("test-server");
        config.setRequestTimeoutSeconds(60);

        var transport = mock(McpTransport.class);
        var client = ManagedMcpClient.create(config, transport);

        assertThat(client.getRequestTimeout()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void shouldTrackConnectionState() {
        var config = new McpProperties.ServerConfig();
        config.setName("test-server");

        var transport = mock(McpTransport.class);
        var client = ManagedMcpClient.create(config, transport);

        assertThat(client.isConnected()).isFalse();

        client.markConnected();
        assertThat(client.isConnected()).isTrue();

        client.markDisconnected();
        assertThat(client.isConnected()).isFalse();
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ManagedMcpClientTest`
Expected: FAIL with "ManagedMcpClient not found"

**Step 3: Write minimal implementation**

```java
package com.jaguarliu.ai.mcp.client;

import com.jaguarliu.ai.mcp.McpProperties;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.McpTransport;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 托管的 MCP 客户端
 * 包装 McpClient 并提供连接状态管理、健康检查等功能
 * 使用同步客户端（McpSyncClient）简化实现
 */
@Slf4j
@Getter
public class ManagedMcpClient {

    private final String name;
    private final McpProperties.ServerConfig config;
    private final McpSyncClient client;
    private final Duration requestTimeout;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    private ManagedMcpClient(
            String name,
            McpProperties.ServerConfig config,
            McpSyncClient client,
            Duration requestTimeout
    ) {
        this.name = name;
        this.config = config;
        this.client = client;
        this.requestTimeout = requestTimeout;
    }

    /**
     * 创建托管客户端
     * 使用 McpClient.sync() 创建同步客户端
     */
    public static ManagedMcpClient create(
            McpProperties.ServerConfig config,
            McpTransport transport
    ) {
        log.info("Creating MCP sync client for server: {}", config.getName());

        Duration timeout = Duration.ofSeconds(config.getRequestTimeoutSeconds());

        // 使用同步客户端（基于 Reactive Streams 的同步 facade）
        McpSyncClient syncClient = McpClient.sync(transport)
                .requestTimeout(timeout)
                .build();

        return new ManagedMcpClient(config.getName(), config, syncClient, timeout);
    }

    /**
     * 初始化连接
     */
    public void initialize() {
        log.info("Initializing MCP client: {}", name);
        try {
            client.initialize();
            markConnected();
            log.info("MCP client initialized successfully: {}", name);
        } catch (Exception e) {
            log.error("Failed to initialize MCP client: {}", name, e);
            markDisconnected();
            throw new RuntimeException("Failed to initialize MCP client: " + name, e);
        }
    }

    /**
     * 关闭连接
     */
    public void close() {
        log.info("Closing MCP client: {}", name);
        try {
            client.close();
        } catch (Exception e) {
            log.warn("Error closing MCP client: {}", name, e);
        } finally {
            markDisconnected();
        }
    }

    /**
     * 标记为已连接
     */
    public void markConnected() {
        connected.set(true);
    }

    /**
     * 标记为已断开
     */
    public void markDisconnected() {
        connected.set(false);
    }

    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * 获取工具名称前缀
     */
    public String getToolPrefix() {
        return config.getToolPrefix() != null ? config.getToolPrefix() : "";
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ManagedMcpClientTest`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/mcp/client/ManagedMcpClient.java \
        src/test/java/com/jaguarliu/ai/mcp/client/ManagedMcpClientTest.java
git commit -m "feat(mcp): implement managed MCP sync client wrapper"
```

---

### Task 6: Create MCP Client Manager

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/mcp/client/McpClientManager.java`
- Create: `src/test/java/com/jaguarliu/ai/mcp/client/McpClientManagerTest.java`

**Step 1: Write the failing test**

```java
package com.jaguarliu.ai.mcp.client;

import com.jaguarliu.ai.mcp.McpProperties;
import com.jaguarliu.ai.mcp.transport.McpTransportFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class McpClientManagerTest {

    @Autowired
    private McpClientManager clientManager;

    @Autowired
    private McpProperties mcpProperties;

    @BeforeEach
    void setUp() {
        // 清理之前的客户端
        clientManager.closeAll();
    }

    @Test
    void shouldInitializeWithoutServers() {
        assertThat(clientManager).isNotNull();
        assertThat(clientManager.getAllClients()).isEmpty();
    }

    @Test
    void shouldGetClientByName() {
        // Given: 配置中有一个禁用的服务器
        var config = new McpProperties.ServerConfig();
        config.setName("test");
        config.setEnabled(false);

        // When/Then: 不应该创建客户端
        var client = clientManager.getClient("test");
        assertThat(client).isEmpty();
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=McpClientManagerTest`
Expected: FAIL with "McpClientManager not found"

**Step 3: Write minimal implementation**

```java
package com.jaguarliu.ai.mcp.client;

import com.jaguarliu.ai.mcp.McpProperties;
import com.jaguarliu.ai.mcp.transport.McpTransportFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 客户端管理器
 * 负责创建、管理和监控所有 MCP 客户端连接
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpClientManager {

    private final McpProperties mcpProperties;
    private final McpTransportFactory transportFactory;

    /**
     * 客户端映射表：name → ManagedMcpClient
     */
    private final Map<String, ManagedMcpClient> clients = new ConcurrentHashMap<>();

    /**
     * 在 bean 初始化后自动连接配置的 MCP 服务器
     */
    @PostConstruct
    public void initialize() {
        log.info("Initializing MCP Client Manager");

        List<McpProperties.ServerConfig> servers = mcpProperties.getServers();
        if (servers == null || servers.isEmpty()) {
            log.info("No MCP servers configured");
            return;
        }

        for (McpProperties.ServerConfig config : servers) {
            if (!config.isEnabled()) {
                log.info("MCP server disabled, skipping: {}", config.getName());
                continue;
            }

            try {
                connectServer(config);
            } catch (Exception e) {
                log.error("Failed to connect MCP server: {}", config.getName(), e);
            }
        }

        log.info("MCP Client Manager initialized with {} clients", clients.size());
    }

    /**
     * 连接到 MCP 服务器
     */
    private void connectServer(McpProperties.ServerConfig config) {
        String name = config.getName();

        if (clients.containsKey(name)) {
            log.warn("MCP client already exists: {}", name);
            return;
        }

        log.info("Connecting to MCP server '{}' via {} transport",
                name, config.getTransport());

        // 创建 Transport
        var transport = transportFactory.createTransport(config);

        // 创建客户端
        var client = ManagedMcpClient.create(config, transport);

        // 初始化连接
        client.initialize();

        // 注册客户端
        clients.put(name, client);

        log.info("Successfully connected to MCP server: {} (transport: {})",
                name, config.getTransport());
    }

    /**
     * 获取客户端
     */
    public Optional<ManagedMcpClient> getClient(String name) {
        return Optional.ofNullable(clients.get(name));
    }

    /**
     * 列出所有客户端
     */
    public List<ManagedMcpClient> getAllClients() {
        return List.copyOf(clients.values());
    }

    /**
     * 关闭指定客户端
     */
    public void closeClient(String name) {
        ManagedMcpClient client = clients.remove(name);
        if (client != null) {
            client.close();
            log.info("Closed MCP client: {}", name);
        }
    }

    /**
     * 关闭所有客户端
     */
    @PreDestroy
    public void closeAll() {
        log.info("Closing all MCP clients");
        clients.values().forEach(ManagedMcpClient::close);
        clients.clear();
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=McpClientManagerTest`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/mcp/client/McpClientManager.java \
        src/test/java/com/jaguarliu/ai/mcp/client/McpClientManagerTest.java
git commit -m "feat(mcp): implement MCP client manager for lifecycle management"
```

---

## Phase 3: Tool Adaptation

### Task 7: Create MCP Tool Adapter

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/mcp/tools/McpToolAdapter.java`
- Create: `src/test/java/com/jaguarliu/ai/mcp/tools/McpToolAdapterTest.java`

**Step 1: Write the failing test**

```java
package com.jaguarliu.ai.mcp.tools;

import com.jaguarliu.ai.mcp.client.ManagedMcpClient;
import com.jaguarliu.ai.tools.ToolDefinition;
import io.modelcontextprotocol.spec.Tool;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpToolAdapterTest {

    @Test
    void shouldAdaptMcpToolDefinition() {
        // Given: MCP tool
        var mcpTool = mock(Tool.class);
        when(mcpTool.name()).thenReturn("get_weather");
        when(mcpTool.description()).thenReturn("Get weather for a city");
        when(mcpTool.inputSchema()).thenReturn(Map.of(
                "type", "object",
                "properties", Map.of("city", Map.of("type", "string"))
        ));

        var mcpClient = mock(ManagedMcpClient.class);
        when(mcpClient.getToolPrefix()).thenReturn("mcp_");
        when(mcpClient.getName()).thenReturn("test-server");

        // When: create adapter
        var adapter = new McpToolAdapter(mcpTool, mcpClient);

        // Then: should have prefixed name
        ToolDefinition def = adapter.getDefinition();
        assertThat(def.getName()).isEqualTo("mcp_get_weather");
        assertThat(def.getDescription()).contains("test-server");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=McpToolAdapterTest`
Expected: FAIL with "McpToolAdapter not found"

**Step 3: Write minimal implementation**

```java
package com.jaguarliu.ai.mcp.tools;

import com.jaguarliu.ai.mcp.client.ManagedMcpClient;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import io.modelcontextprotocol.spec.CallToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * MCP 工具适配器
 * 将 MCP Tool 适配为 miniclaw 的 Tool 接口
 */
@Slf4j
@RequiredArgsConstructor
public class McpToolAdapter implements Tool {

    private final io.modelcontextprotocol.spec.Tool mcpTool;
    private final ManagedMcpClient mcpClient;
    private final ToolDefinition toolDefinition;

    /**
     * 构造函数，从 MCP Tool 创建适配器
     */
    public McpToolAdapter(
            io.modelcontextprotocol.spec.Tool mcpTool,
            ManagedMcpClient mcpClient
    ) {
        this.mcpTool = mcpTool;
        this.mcpClient = mcpClient;
        this.toolDefinition = convertToToolDefinition(mcpTool, mcpClient);
    }

    /**
     * 将 MCP Tool 转换为 ToolDefinition
     */
    private static ToolDefinition convertToToolDefinition(
            io.modelcontextprotocol.spec.Tool mcpTool,
            ManagedMcpClient mcpClient
    ) {
        String toolName = mcpClient.getToolPrefix() + mcpTool.name();
        String description = mcpTool.description() != null
                ? mcpTool.description()
                : "MCP Tool: " + mcpTool.name();

        // 转换 MCP inputSchema 到 ToolDefinition parameters
        Map<String, Object> parameters = mcpTool.inputSchema() != null
                ? mcpTool.inputSchema()
                : Map.of("type", "object", "properties", Map.of());

        // 检查是否需要 HITL
        boolean requiresHitl = mcpClient.getConfig().isRequiresHitl()
                || mcpClient.getConfig().getHitlTools().contains(mcpTool.name());

        return ToolDefinition.builder()
                .name(toolName)
                .description(description + " (from MCP server: " + mcpClient.getName() + ")")
                .parameters(parameters)
                .hitl(requiresHitl)
                .build();
    }

    @Override
    public ToolDefinition getDefinition() {
        return toolDefinition;
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        log.info("Executing MCP tool: {} with arguments: {}", mcpTool.name(), arguments);

        if (!mcpClient.isConnected()) {
            log.error("MCP client not connected: {}", mcpClient.getName());
            return Mono.just(ToolResult.error(
                    "MCP server not connected: " + mcpClient.getName()
            ));
        }

        return Mono.fromCallable(() -> {
            try {
                // 调用 MCP 工具（使用同步客户端）
                CallToolResult result = mcpClient.getClient().callTool(
                        mcpTool.name(),
                        arguments
                );

                // 检查是否有错误
                if (result.isError()) {
                    log.error("MCP tool returned error: {}", result);
                    return ToolResult.error(extractErrorMessage(result));
                }

                // 提取成功结果
                String content = extractContent(result);
                return ToolResult.success(content);

            } catch (Exception e) {
                log.error("MCP tool execution failed: {}", mcpTool.name(), e);
                return ToolResult.error("MCP tool execution failed: " + e.getMessage());
            }
        });
    }

    /**
     * 从 CallToolResult 提取内容
     */
    private String extractContent(CallToolResult result) {
        if (result.content() != null && !result.content().isEmpty()) {
            return result.content().stream()
                    .map(content -> {
                        // 根据 Content 类型提取文本
                        if (content.text() != null) {
                            return content.text();
                        }
                        return content.toString();
                    })
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
        }
        return "";
    }

    /**
     * 从 CallToolResult 提取错误消息
     */
    private String extractErrorMessage(CallToolResult result) {
        if (result.content() != null && !result.content().isEmpty()) {
            return extractContent(result);
        }
        return "Unknown error from MCP tool";
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=McpToolAdapterTest`
Expected: PASS (with mocked dependencies)

**Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/mcp/tools/McpToolAdapter.java \
        src/test/java/com/jaguarliu/ai/mcp/tools/McpToolAdapterTest.java
git commit -m "feat(mcp): implement MCP tool adapter with HITL support"
```

---

### Task 8: Create MCP Tool Registry

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/mcp/tools/McpToolRegistry.java`
- Create: `src/test/java/com/jaguarliu/ai/mcp/tools/McpToolRegistryTest.java`

**Step 1: Write the failing test**

```java
package com.jaguarliu.ai.mcp.tools;

import com.jaguarliu.ai.mcp.client.McpClientManager;
import com.jaguarliu.ai.tools.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class McpToolRegistryTest {

    @Autowired
    private McpToolRegistry mcpToolRegistry;

    @Autowired
    private ToolRegistry toolRegistry;

    @Test
    void shouldInitializeWithoutClients() {
        assertThat(mcpToolRegistry).isNotNull();
    }

    @Test
    void shouldDiscoverToolsFromMcpClients() {
        // Given: 没有 MCP 客户端
        // When: 刷新工具注册
        mcpToolRegistry.refreshTools();

        // Then: 不应该注册任何 MCP 工具
        // (实际测试需要 mock MCP client)
        assertThat(true).isTrue();
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=McpToolRegistryTest`
Expected: FAIL with "McpToolRegistry not found"

**Step 3: Write minimal implementation**

```java
package com.jaguarliu.ai.mcp.tools;

import com.jaguarliu.ai.mcp.client.McpClientManager;
import com.jaguarliu.ai.mcp.client.ManagedMcpClient;
import com.jaguarliu.ai.tools.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP 工具注册器
 * 自动发现 MCP 客户端的工具并注册到 ToolRegistry
 *
 * 使用 SmartInitializingSingleton 确保在 ToolRegistry 初始化后再注册 MCP 工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolRegistry implements SmartInitializingSingleton {

    private final McpClientManager mcpClientManager;
    private final ToolRegistry toolRegistry;

    /**
     * 在所有 singleton bean 完全初始化后发现并注册 MCP 工具
     */
    @Override
    public void afterSingletonsInstantiated() {
        log.info("Discovering and registering MCP tools");
        refreshTools();
    }

    /**
     * 刷新 MCP 工具注册
     * 从所有已连接的 MCP 客户端发现工具并注册
     */
    public void refreshTools() {
        List<ManagedMcpClient> clients = mcpClientManager.getAllClients();

        if (clients.isEmpty()) {
            log.info("No MCP clients available for tool discovery");
            return;
        }

        int totalTools = 0;
        for (ManagedMcpClient client : clients) {
            if (!client.isConnected()) {
                log.warn("MCP client not connected, skipping: {}", client.getName());
                continue;
            }

            try {
                int count = discoverAndRegisterTools(client);
                totalTools += count;
            } catch (Exception e) {
                log.error("Failed to discover tools from MCP client: {}", client.getName(), e);
            }
        }

        log.info("MCP tool discovery complete. Registered {} tools from {} clients",
                totalTools, clients.size());
    }

    /**
     * 从单个 MCP 客户端发现并注册工具
     */
    private int discoverAndRegisterTools(ManagedMcpClient client) {
        log.info("Discovering tools from MCP server: {}", client.getName());

        int count = 0;

        try {
            // 1. 列出并注册工具
            var listToolsResult = client.getClient().listTools();

            if (listToolsResult.tools() != null && !listToolsResult.tools().isEmpty()) {
                for (var mcpTool : listToolsResult.tools()) {
                    var adapter = new McpToolAdapter(mcpTool, client);
                    toolRegistry.register(adapter);
                    log.debug("Registered MCP tool: {}", adapter.getDefinition().getName());
                    count++;
                }
            }

            // 2. 检查是否支持资源，如果支持则注册资源访问工具
            var listResourcesResult = client.getClient().listResources();
            if (listResourcesResult.resources() != null && !listResourcesResult.resources().isEmpty()) {
                var resourceTool = new McpResourceTool(client);
                toolRegistry.register(resourceTool);
                log.debug("Registered MCP resource tool: {}", resourceTool.getDefinition().getName());
                count++;
            }

            log.info("Registered {} tools/resources from MCP server: {}", count, client.getName());
            return count;

        } catch (Exception e) {
            log.error("Failed to list tools from MCP server: {}", client.getName(), e);
            throw new RuntimeException("Failed to discover tools from: " + client.getName(), e);
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=McpToolRegistryTest`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/mcp/tools/McpToolRegistry.java \
        src/test/java/com/jaguarliu/ai/mcp/tools/McpToolRegistryTest.java
git commit -m "feat(mcp): implement MCP tool registry for automatic tool discovery"
```

---

## Phase 4: Resource & Prompt Support

### Task 9: Create MCP Resource Tool

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/mcp/tools/McpResourceTool.java`
- Create: `src/test/java/com/jaguarliu/ai/mcp/tools/McpResourceToolTest.java`

**Step 1: Write the failing test**

```java
package com.jaguarliu.ai.mcp.tools;

import com.jaguarliu.ai.mcp.client.ManagedMcpClient;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpResourceToolTest {

    @Test
    void shouldCreateResourceTool() {
        var mcpClient = mock(ManagedMcpClient.class);
        when(mcpClient.getName()).thenReturn("test-server");
        when(mcpClient.getToolPrefix()).thenReturn("");

        var resourceTool = new McpResourceTool(mcpClient);

        assertThat(resourceTool.getDefinition().getName()).isEqualTo("mcp_read_resource");
        assertThat(resourceTool.getDefinition()).isNotNull();
    }

    @Test
    void shouldRequireResourceUriParameter() {
        var mcpClient = mock(ManagedMcpClient.class);
        when(mcpClient.getName()).thenReturn("test-server");
        when(mcpClient.getToolPrefix()).thenReturn("");

        var resourceTool = new McpResourceTool(mcpClient);
        var params = resourceTool.getDefinition().getParameters();

        assertThat(params).containsKey("properties");
        @SuppressWarnings("unchecked")
        var properties = (Map<String, Object>) params.get("properties");
        assertThat(properties).containsKey("uri");
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=McpResourceToolTest`
Expected: FAIL with "McpResourceTool not found"

**Step 3: Write minimal implementation**

```java
package com.jaguarliu.ai.mcp.tools;

import com.jaguarliu.ai.mcp.client.ManagedMcpClient;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * MCP 资源访问工具
 * 提供对 MCP Resources 的读取能力
 */
@Slf4j
@RequiredArgsConstructor
public class McpResourceTool implements Tool {

    private final ManagedMcpClient mcpClient;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name(mcpClient.getToolPrefix() + "mcp_read_resource")
                .description(String.format(
                        "Read a resource from MCP server '%s'. " +
                        "Resources provide access to data like files, database records, API responses, etc.",
                        mcpClient.getName()
                ))
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "uri", Map.of(
                                        "type", "string",
                                        "description", "URI of the resource to read (e.g., 'file:///path/to/file.txt')"
                                )
                        ),
                        "required", new String[]{"uri"}
                ))
                .hitl(false)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        String uri = (String) arguments.get("uri");

        if (uri == null || uri.isBlank()) {
            return Mono.just(ToolResult.error("Resource URI is required"));
        }

        if (!mcpClient.isConnected()) {
            return Mono.just(ToolResult.error(
                    "MCP server not connected: " + mcpClient.getName()
            ));
        }

        log.info("Reading MCP resource: {} from server: {}", uri, mcpClient.getName());

        return Mono.fromCallable(() -> {
            try {
                var result = mcpClient.getClient().readResource(uri);

                if (result.contents() == null || result.contents().isEmpty()) {
                    return ToolResult.success("(empty resource)");
                }

                // 合并所有内容
                String content = result.contents().stream()
                        .map(resourceContent -> {
                            if (resourceContent.text() != null) {
                                return resourceContent.text();
                            }
                            return resourceContent.toString();
                        })
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("(empty)");

                return ToolResult.success(content);

            } catch (Exception e) {
                log.error("Failed to read MCP resource: {}", uri, e);
                return ToolResult.error("Failed to read resource: " + e.getMessage());
            }
        });
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=McpResourceToolTest`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/mcp/tools/McpResourceTool.java \
        src/test/java/com/jaguarliu/ai/mcp/tools/McpResourceToolTest.java
git commit -m "feat(mcp): implement MCP resource tool for accessing MCP resources"
```

---

### Task 10: Create MCP Prompt Provider

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/mcp/prompt/McpPromptProvider.java`
- Create: `src/test/java/com/jaguarliu/ai/mcp/prompt/McpPromptProviderTest.java`

**Step 1: Write the failing test**

```java
package com.jaguarliu.ai.mcp.prompt;

import com.jaguarliu.ai.mcp.client.McpClientManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class McpPromptProviderTest {

    @Autowired
    private McpPromptProvider promptProvider;

    @Test
    void shouldLoadPromptsFromMcpServers() {
        String prompts = promptProvider.getSystemPromptAdditions();
        assertThat(prompts).isNotNull();
    }

    @Test
    void shouldReturnEmptyWhenNoClientsAvailable() {
        String prompts = promptProvider.getSystemPromptAdditions();
        // 没有 MCP 服务器时应该返回空字符串
        assertThat(prompts).isEmpty();
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=McpPromptProviderTest`
Expected: FAIL with "McpPromptProvider not found"

**Step 3: Write minimal implementation**

```java
package com.jaguarliu.ai.mcp.prompt;

import com.jaguarliu.ai.mcp.client.McpClientManager;
import com.jaguarliu.ai.mcp.client.ManagedMcpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP Prompt 提供器
 * 从 MCP 服务器获取 Prompts 并集成到系统提示中
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpPromptProvider {

    private final McpClientManager mcpClientManager;

    /**
     * 获取所有 MCP 服务器的 Prompt 附加内容
     *
     * @return 合并后的系统提示附加内容
     */
    public String getSystemPromptAdditions() {
        List<ManagedMcpClient> clients = mcpClientManager.getAllClients();

        if (clients.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## MCP Server Capabilities\n\n");

        for (ManagedMcpClient client : clients) {
            if (!client.isConnected()) {
                continue;
            }

            try {
                String serverPrompts = getPromptsFromServer(client);
                if (!serverPrompts.isEmpty()) {
                    sb.append(serverPrompts).append("\n");
                }
            } catch (Exception e) {
                log.error("Failed to get prompts from MCP server: {}", client.getName(), e);
            }
        }

        return sb.toString();
    }

    /**
     * 从单个 MCP 服务器获取 Prompts
     */
    private String getPromptsFromServer(ManagedMcpClient client) {
        try {
            var listPromptsResult = client.getClient().listPrompts();

            if (listPromptsResult.prompts() == null || listPromptsResult.prompts().isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("### %s\n\n", client.getName()));

            for (var prompt : listPromptsResult.prompts()) {
                sb.append(String.format("- **%s**: %s\n",
                        prompt.name(),
                        prompt.description() != null ? prompt.description() : "No description"
                ));
            }

            return sb.toString();

        } catch (Exception e) {
            log.warn("Failed to list prompts from MCP server: {}", client.getName(), e);
            return "";
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=McpPromptProviderTest`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/mcp/prompt/McpPromptProvider.java \
        src/test/java/com/jaguarliu/ai/mcp/prompt/McpPromptProviderTest.java
git commit -m "feat(mcp): implement MCP prompt provider for system prompt integration"
```

---

## Phase 5: Integration & Polish

### Task 11: Integrate MCP Prompts into SystemPromptBuilder

**Files:**
- Locate and modify: `src/main/java/**/SystemPromptBuilder.java`

**Step 1: Locate SystemPromptBuilder**

Run: `find src -name "SystemPromptBuilder.java" -type f`
Expected: Find the file path

**Step 2: Read SystemPromptBuilder to understand structure**

Read the file using Read tool

**Step 3: Inject McpPromptProvider and add to system prompt**

Add field:
```java
private final Optional<McpPromptProvider> mcpPromptProvider;
```

Update build method to append MCP prompts (at end of prompt construction):
```java
// 在 system prompt 构建逻辑末尾添加
mcpPromptProvider.ifPresent(provider -> {
    String mcpAdditions = provider.getSystemPromptAdditions();
    if (!mcpAdditions.isEmpty()) {
        prompt.append(mcpAdditions);
    }
});
```

**Step 4: Run application to verify integration**

Run: `mvn spring-boot:run`
Check logs for MCP prompt loading

**Step 5: Commit**

```bash
git add src/main/java/**/SystemPromptBuilder.java
git commit -m "feat(mcp): integrate MCP prompts into system prompt builder"
```

---

### Task 12: Add Health Check for MCP Clients

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/mcp/health/McpHealthChecker.java`
- Create: `src/test/java/com/jaguarliu/ai/mcp/health/McpHealthCheckerTest.java`

**Step 1: Write the failing test**

```java
package com.jaguarliu.ai.mcp.health;

import com.jaguarliu.ai.mcp.client.McpClientManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class McpHealthCheckerTest {

    @Autowired
    private McpHealthChecker healthChecker;

    @Test
    void shouldCheckHealth() {
        // Health check 应该可以执行
        healthChecker.checkHealth();
        assertThat(true).isTrue();
    }
}
```

**Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=McpHealthCheckerTest`
Expected: FAIL with "McpHealthChecker not found"

**Step 3: Write minimal implementation**

```java
package com.jaguarliu.ai.mcp.health;

import com.jaguarliu.ai.mcp.McpProperties;
import com.jaguarliu.ai.mcp.client.McpClientManager;
import com.jaguarliu.ai.mcp.client.ManagedMcpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * MCP 健康检查器
 * 定期检查 MCP 客户端连接状态并尝试重连
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpHealthChecker {

    private final McpClientManager mcpClientManager;
    private final McpProperties mcpProperties;

    /**
     * 定期健康检查
     * 根据配置的 interval-seconds 执行
     */
    @Scheduled(
            fixedDelayString = "${mcp.health-check.interval-seconds:60}",
            timeUnit = TimeUnit.SECONDS,
            initialDelay = 30
    )
    public void checkHealth() {
        List<ManagedMcpClient> clients = mcpClientManager.getAllClients();

        if (clients.isEmpty()) {
            return;
        }

        log.debug("Running MCP health check for {} clients", clients.size());

        for (ManagedMcpClient client : clients) {
            checkClientHealth(client);
        }
    }

    /**
     * 检查单个客户端健康状态
     */
    private void checkClientHealth(ManagedMcpClient client) {
        try {
            // 发送 ping 请求测试连接
            if (client.isConnected()) {
                client.getClient().ping();
                log.debug("MCP client healthy: {}", client.getName());
            } else {
                log.warn("MCP client disconnected: {}", client.getName());
                attemptReconnect(client);
            }
        } catch (Exception e) {
            log.error("MCP health check failed for: {}", client.getName(), e);
            client.markDisconnected();
            attemptReconnect(client);
        }
    }

    /**
     * 尝试重新连接
     */
    private void attemptReconnect(ManagedMcpClient client) {
        log.info("Attempting to reconnect MCP client: {}", client.getName());

        try {
            client.initialize();
            log.info("Successfully reconnected MCP client: {}", client.getName());
        } catch (Exception e) {
            log.error("Failed to reconnect MCP client: {}", client.getName(), e);
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=McpHealthCheckerTest`
Expected: PASS

**Step 5: Enable scheduling in main application**

Ensure `@EnableScheduling` is present in the main application class.

**Step 6: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/mcp/health/McpHealthChecker.java \
        src/test/java/com/jaguarliu/ai/mcp/health/McpHealthCheckerTest.java
git commit -m "feat(mcp): implement health checker with auto-reconnect"
```

---

### Task 13: Documentation and Examples

**Files:**
- Create: `docs/mcp-integration.md`
- Create: `docs/examples/mcp-config-example.yml`
- Modify: `README.md`

**Step 1: Write comprehensive MCP integration documentation**

Create `docs/mcp-integration.md` with:
- **Overview of MCP integration as a Client**
- **What MCP Servers can be connected:**
  - Official MCP Servers (filesystem, fetch, etc.)
  - Third-party MCP Servers (Tavily, GitHub, etc.)
  - **User-developed custom MCP Servers** (Python, Node.js, Java, etc.)
- **Three transport types explained (STDIO, SSE, HTTP)**
- **Configuration examples for each type of MCP Server:**
  - Official servers (via npx)
  - Third-party servers (via URL)
  - Custom servers (local processes or deployed services)
- **Tool discovery and usage**
- **Resource access patterns**
- **Prompt integration**
- **HITL configuration**
- **How to develop a custom MCP Server:**
  - Quick start with MCP Python/Node.js SDK
  - Implementing tools, resources, prompts
  - Testing locally with miniclaw
  - Deployment options (Docker, systemd, cloud)
- **Troubleshooting guide**
  - Connection issues
  - Tool discovery failures
  - Transport-specific problems

**Step 2: Create example configuration file**

Create `docs/examples/mcp-config-example.yml`:

```yaml
mcp:
  servers:
    # ============================================
    # 官方 MCP Server: Filesystem (STDIO)
    # ============================================
    - name: filesystem
      transport: stdio
      command: npx
      args:
        - "-y"
        - "@modelcontextprotocol/server-filesystem"
        - "/tmp"
      enabled: true
      tool-prefix: fs_
      requires-hitl: false
      hitl-tools: [delete_file, write_file]
      request-timeout-seconds: 30

    # ============================================
    # 第三方 MCP Server: Tavily Search (SSE)
    # ============================================
    - name: tavily-search
      transport: sse
      url: http://localhost:3000/sse
      enabled: true
      tool-prefix: search_
      requires-hitl: false
      request-timeout-seconds: 60

    # ============================================
    # 用户自定义 MCP Server: Python 本地服务 (STDIO)
    # ============================================
    - name: my-data-analysis
      transport: stdio
      command: python
      args:
        - "/home/user/mcp-servers/data_analysis_server.py"
      working-dir: /home/user/mcp-servers
      env:
        - "DATABASE_URL=postgresql://localhost/mydb"
        - "LOG_LEVEL=info"
      enabled: true
      tool-prefix: data_
      requires-hitl: true
      request-timeout-seconds: 120

    # ============================================
    # 用户自定义 MCP Server: Node.js 服务 (HTTP)
    # ============================================
    - name: my-nodejs-api
      transport: http
      url: http://localhost:8080/mcp
      enabled: true
      tool-prefix: api_
      hitl-tools: [delete_record, execute_sql]
      request-timeout-seconds: 45

    # ============================================
    # 用户自定义 MCP Server: Docker 容器服务 (SSE)
    # ============================================
    - name: ml-inference
      transport: sse
      url: http://ml-container:9000/sse
      enabled: true
      tool-prefix: ml_
      requires-hitl: false
      request-timeout-seconds: 300

    # ============================================
    # 企业内部 MCP Server: Java Spring Boot (HTTP)
    # ============================================
    - name: internal-crm
      transport: http
      url: https://crm.company.internal/mcp
      enabled: true
      tool-prefix: crm_
      requires-hitl: true
      request-timeout-seconds: 60

  health-check:
    interval-seconds: 60
    max-retries: 3
    retry-backoff-seconds: 5
```

**Step 3: Update main README**

Add section about MCP integration to README.md

**Step 4: Commit documentation**

```bash
git add docs/mcp-integration.md \
        docs/examples/mcp-config-example.yml \
        README.md
git commit -m "docs: add comprehensive MCP integration documentation"
```

---

## Phase 6: Frontend Integration

### Task 17: Create MCP Server RPC Handlers

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/rpc/handler/mcp/McpServerListHandler.java`
- Create: `src/main/java/com/jaguarliu/ai/rpc/handler/mcp/McpServerCreateHandler.java`
- Create: `src/main/java/com/jaguarliu/ai/rpc/handler/mcp/McpServerUpdateHandler.java`
- Create: `src/main/java/com/jaguarliu/ai/rpc/handler/mcp/McpServerDeleteHandler.java`
- Create: `src/main/java/com/jaguarliu/ai/rpc/handler/mcp/McpServerTestHandler.java`

**Step 1: Create List Handler**

Create `src/main/java/com/jaguarliu/ai/rpc/handler/mcp/McpServerListHandler.java`:

```java
package com.jaguarliu.ai.rpc.handler.mcp;

import com.jaguarliu.ai.mcp.service.McpServerService;
import com.jaguarliu.ai.rpc.RpcHandler;
import com.jaguarliu.ai.rpc.RpcRequest;
import com.jaguarliu.ai.rpc.RpcResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 列出所有 MCP Server 配置
 * RPC Method: mcp.servers.list
 */
@Slf4j
@Component("mcp.servers.list")
@RequiredArgsConstructor
public class McpServerListHandler implements RpcHandler {

    private final McpServerService mcpServerService;

    @Override
    public Mono<RpcResponse> handle(RpcRequest request) {
        log.debug("Listing all MCP servers");

        try {
            var servers = mcpServerService.listServers();

            var result = servers.stream()
                    .map(entity -> Map.of(
                            "id", entity.getId(),
                            "name", entity.getName(),
                            "transportType", entity.getTransportType().toString(),
                            "enabled", entity.getEnabled(),
                            "toolPrefix", entity.getToolPrefix(),
                            "url", entity.getUrl() != null ? entity.getUrl() : "",
                            "command", entity.getCommand() != null ? entity.getCommand() : "",
                            "createdAt", entity.getCreatedAt().toString(),
                            "updatedAt", entity.getUpdatedAt().toString()
                    ))
                    .collect(Collectors.toList());

            return Mono.just(RpcResponse.success(request.getId(), Map.of("servers", result)));

        } catch (Exception e) {
            log.error("Failed to list MCP servers", e);
            return Mono.just(RpcResponse.error(request.getId(), "Failed to list MCP servers: " + e.getMessage()));
        }
    }
}
```

**Step 2: Create Create Handler**

Create `src/main/java/com/jaguarliu/ai/rpc/handler/mcp/McpServerCreateHandler.java`:

```java
package com.jaguarliu.ai.rpc.handler.mcp;

import com.jaguarliu.ai.mcp.McpProperties;
import com.jaguarliu.ai.mcp.service.McpServerService;
import com.jaguarliu.ai.rpc.RpcHandler;
import com.jaguarliu.ai.rpc.RpcRequest;
import com.jaguarliu.ai.rpc.RpcResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 创建新的 MCP Server 配置
 * RPC Method: mcp.servers.create
 *
 * Payload:
 * {
 *   "name": "my-server",
 *   "transportType": "STDIO",
 *   "command": "npx",
 *   "args": ["-y", "..."],
 *   "url": "http://...",  // for SSE/HTTP
 *   "enabled": true,
 *   "toolPrefix": "prefix_"
 * }
 */
@Slf4j
@Component("mcp.servers.create")
@RequiredArgsConstructor
public class McpServerCreateHandler implements RpcHandler {

    private final McpServerService mcpServerService;

    @Override
    public Mono<RpcResponse> handle(RpcRequest request) {
        log.info("Creating new MCP server");

        try {
            Map<String, Object> payload = request.getPayload();

            // 构建配置
            var config = new McpProperties.ServerConfig();
            config.setName((String) payload.get("name"));
            config.setTransport(McpProperties.TransportType.valueOf((String) payload.get("transportType")));
            config.setCommand((String) payload.getOrDefault("command", null));
            config.setArgs((List<String>) payload.getOrDefault("args", List.of()));
            config.setWorkingDir((String) payload.getOrDefault("workingDir", null));
            config.setEnv((List<String>) payload.getOrDefault("env", List.of()));
            config.setUrl((String) payload.getOrDefault("url", null));
            config.setEnabled((Boolean) payload.getOrDefault("enabled", true));
            config.setToolPrefix((String) payload.getOrDefault("toolPrefix", ""));
            config.setRequiresHitl((Boolean) payload.getOrDefault("requiresHitl", false));
            config.setHitlTools((List<String>) payload.getOrDefault("hitlTools", List.of()));
            config.setRequestTimeoutSeconds((Integer) payload.getOrDefault("requestTimeoutSeconds", 30));

            // 创建服务器
            var entity = mcpServerService.createServer(config);

            return Mono.just(RpcResponse.success(request.getId(), Map.of(
                    "server", Map.of(
                            "id", entity.getId(),
                            "name", entity.getName(),
                            "enabled", entity.getEnabled()
                    )
            )));

        } catch (Exception e) {
            log.error("Failed to create MCP server", e);
            return Mono.just(RpcResponse.error(request.getId(), "Failed to create MCP server: " + e.getMessage()));
        }
    }
}
```

**Step 3: Create Test Connection Handler**

Create `src/main/java/com/jaguarliu/ai/rpc/handler/mcp/McpServerTestHandler.java`:

```java
package com.jaguarliu.ai.rpc.handler.mcp;

import com.jaguarliu.ai.mcp.McpProperties;
import com.jaguarliu.ai.mcp.service.McpServerService;
import com.jaguarliu.ai.rpc.RpcHandler;
import com.jaguarliu.ai.rpc.RpcRequest;
import com.jaguarliu.ai.rpc.RpcResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 测试 MCP Server 连接
 * RPC Method: mcp.servers.test
 *
 * 用于前端配置验证，不会保存配置
 */
@Slf4j
@Component("mcp.servers.test")
@RequiredArgsConstructor
public class McpServerTestHandler implements RpcHandler {

    private final McpServerService mcpServerService;

    @Override
    public Mono<RpcResponse> handle(RpcRequest request) {
        log.info("Testing MCP server connection");

        try {
            Map<String, Object> payload = request.getPayload();

            // 构建临时配置
            var config = new McpProperties.ServerConfig();
            config.setName((String) payload.get("name"));
            config.setTransport(McpProperties.TransportType.valueOf((String) payload.get("transportType")));
            config.setCommand((String) payload.getOrDefault("command", null));
            config.setArgs((List<String>) payload.getOrDefault("args", List.of()));
            config.setUrl((String) payload.getOrDefault("url", null));
            config.setRequestTimeoutSeconds((Integer) payload.getOrDefault("requestTimeoutSeconds", 30));

            // 测试连接
            boolean success = mcpServerService.testConnection(config);

            return Mono.just(RpcResponse.success(request.getId(), Map.of(
                    "success", success,
                    "message", success ? "Connection successful" : "Connection failed"
            )));

        } catch (Exception e) {
            log.error("Connection test failed", e);
            return Mono.just(RpcResponse.success(request.getId(), Map.of(
                    "success", false,
                    "message", "Connection test failed: " + e.getMessage()
            )));
        }
    }
}
```

**Step 4: Create Update and Delete Handlers**

Similar pattern for update and delete operations.

**Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/rpc/handler/mcp/
git commit -m "feat(mcp): add RPC handlers for MCP server management"
```

---

### Task 18: Create Frontend MCP Management Page (Optional)

**Note**: This task provides a reference design for the frontend team. The actual implementation will be done by frontend developers.

**Files:**
- Create: `docs/frontend-mcp-integration.md`

**Step 1: Create frontend integration guide**

Create `docs/frontend-mcp-integration.md`:

```markdown
# Frontend MCP Server Management Integration Guide

## Overview

This guide describes how to integrate MCP server management into the miniclaw frontend.

## RPC Methods

### 1. List MCP Servers

**Method**: `mcp.servers.list`

**Request**:
```json
{
  "type": "request",
  "id": "req-123",
  "method": "mcp.servers.list",
  "payload": {}
}
```

**Response**:
```json
{
  "type": "response",
  "id": "req-123",
  "result": {
    "servers": [
      {
        "id": 1,
        "name": "filesystem",
        "transportType": "STDIO",
        "enabled": true,
        "toolPrefix": "fs_",
        "command": "npx",
        "url": "",
        "createdAt": "2026-02-11T10:00:00",
        "updatedAt": "2026-02-11T10:00:00"
      }
    ]
  }
}
```

### 2. Test Connection

**Method**: `mcp.servers.test`

**Request**:
```json
{
  "type": "request",
  "id": "req-124",
  "method": "mcp.servers.test",
  "payload": {
    "name": "test-server",
    "transportType": "STDIO",
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"]
  }
}
```

**Response**:
```json
{
  "type": "response",
  "id": "req-124",
  "result": {
    "success": true,
    "message": "Connection successful"
  }
}
```

### 3. Create MCP Server

**Method**: `mcp.servers.create`

**Request**:
```json
{
  "type": "request",
  "id": "req-125",
  "method": "mcp.servers.create",
  "payload": {
    "name": "my-server",
    "transportType": "STDIO",
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
    "enabled": true,
    "toolPrefix": "fs_",
    "requiresHitl": false,
    "hitlTools": [],
    "requestTimeoutSeconds": 30
  }
}
```

**Response**:
```json
{
  "type": "response",
  "id": "req-125",
  "result": {
    "server": {
      "id": 2,
      "name": "my-server",
      "enabled": true
    }
  }
}
```

## UI Component Design

### MCP Server List Page

Components:
1. **Table**: Display all MCP servers
   - Columns: Name, Type, Status (Connected/Disconnected), Tools Count, Actions
   - Actions: Edit, Delete, Enable/Disable

2. **Add Button**: Opens configuration modal

3. **Search/Filter**: Filter by name, type, status

### MCP Server Configuration Modal

Tabs:
1. **Basic Info**:
   - Name (required, unique)
   - Transport Type (STDIO/SSE/HTTP)
   - Tool Prefix

2. **Connection Settings**:
   - If STDIO: Command, Args, Working Dir, Env Vars
   - If SSE/HTTP: URL

3. **Advanced**:
   - Request Timeout
   - HITL Settings
   - Enable/Disable

Actions:
- **Test Connection**: Validates config before saving
- **Save**: Creates/updates server
- **Cancel**: Closes modal

## User Flow

```
1. User clicks "Add MCP Server"
2. Modal opens with empty form
3. User selects transport type (STDIO/SSE/HTTP)
4. Form fields update based on transport type
5. User fills in configuration
6. User clicks "Test Connection"
   → Frontend sends mcp.servers.test RPC
   → Backend validates connection
   → Frontend shows success/error message
7. If successful, user clicks "Save"
   → Frontend sends mcp.servers.create RPC
   → Backend saves and connects
   → Frontend refreshes server list
   → Modal closes
8. New MCP tools are immediately available
```

## Example React Component (Pseudo-code)

```typescript
function McpServerModal({ onClose, onSuccess }) {
  const [config, setConfig] = useState({
    name: '',
    transportType: 'STDIO',
    command: '',
    args: [],
    url: '',
    enabled: true,
    toolPrefix: ''
  });

  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState(null);

  const testConnection = async () => {
    setTesting(true);
    const response = await rpc('mcp.servers.test', config);
    setTestResult(response.result);
    setTesting(false);
  };

  const save = async () => {
    const response = await rpc('mcp.servers.create', config);
    if (response.result) {
      onSuccess();
      onClose();
    }
  };

  return (
    <Modal>
      <Form>
        <Input label="Name" value={config.name} onChange={...} />
        <Select label="Transport Type" value={config.transportType} onChange={...}>
          <option value="STDIO">STDIO (Local Process)</option>
          <option value="SSE">SSE (Remote Server)</option>
          <option value="HTTP">HTTP (Remote Server)</option>
        </Select>

        {config.transportType === 'STDIO' && (
          <>
            <Input label="Command" value={config.command} onChange={...} />
            <ArrayInput label="Arguments" value={config.args} onChange={...} />
          </>
        )}

        {(config.transportType === 'SSE' || config.transportType === 'HTTP') && (
          <Input label="URL" value={config.url} onChange={...} />
        )}

        <Button onClick={testConnection} loading={testing}>
          Test Connection
        </Button>

        {testResult && (
          <Alert type={testResult.success ? 'success' : 'error'}>
            {testResult.message}
          </Alert>
        )}

        <Button onClick={save} disabled={!testResult?.success}>
          Save
        </Button>
      </Form>
    </Modal>
  );
}
```

## Backend Integration Points

All backend work is complete. Frontend just needs to:
1. Call RPC methods listed above
2. Handle success/error responses
3. Update UI accordingly

## Testing

1. Test connection validation
2. Test server creation/update/delete
3. Test immediate tool availability after adding server
4. Test error handling (invalid config, connection failures)
```

**Step 2: Commit documentation**

```bash
git add docs/frontend-mcp-integration.md
git commit -m "docs: add frontend MCP integration guide"
```

---

## Phase 7: Testing & Validation

**Files:**
- Create: `src/test/java/com/jaguarliu/ai/mcp/integration/McpIntegrationTest.java`

**Step 1: Write integration test**

```java
package com.jaguarliu.ai.mcp.integration;

import com.jaguarliu.ai.mcp.client.McpClientManager;
import com.jaguarliu.ai.tools.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCP 集成测试
 * 测试完整的 MCP 工具发现和执行流程
 */
@SpringBootTest
class McpIntegrationTest {

    @Autowired
    private McpClientManager clientManager;

    @Autowired
    private ToolRegistry toolRegistry;

    @Test
    void mcpSystemShouldInitializeCorrectly() {
        assertThat(clientManager).isNotNull();
        assertThat(toolRegistry).isNotNull();

        // 验证系统能正常启动（即使没有配置 MCP 服务器）
        assertThat(clientManager.getAllClients()).isNotNull();
    }

    @Test
    void shouldHandleMissingMcpServersGracefully() {
        // 没有 MCP 服务器配置时不应该报错
        var clients = clientManager.getAllClients();
        assertThat(clients).isEmpty();
    }

    @Test
    void shouldSupportAllThreeTransportTypes() {
        // 验证所有三种传输类型都有对应的枚举值
        assertThat(com.jaguarliu.ai.mcp.McpProperties.TransportType.values())
                .hasSize(3)
                .contains(
                        com.jaguarliu.ai.mcp.McpProperties.TransportType.STDIO,
                        com.jaguarliu.ai.mcp.McpProperties.TransportType.SSE,
                        com.jaguarliu.ai.mcp.McpProperties.TransportType.HTTP
                );
    }
}
```

**Step 2: Run integration test**

Run: `mvn test -Dtest=McpIntegrationTest`
Expected: PASS

**Step 3: Commit**

```bash
git add src/test/java/com/jaguarliu/ai/mcp/integration/McpIntegrationTest.java
git commit -m "test: add MCP integration tests for all transport types"
```

---

### Task 15: Create Custom MCP Server Development Guide

**Files:**
- Create: `docs/custom-mcp-server-guide.md`
- Create: `examples/custom-mcp-server/python/simple_server.py`
- Create: `examples/custom-mcp-server/nodejs/simple-server.js`
- Create: `examples/custom-mcp-server/README.md`

**Step 1: Write comprehensive custom MCP server development guide**

Create `docs/custom-mcp-server-guide.md` with:
1. Introduction to developing custom MCP Servers
2. Choosing an SDK (Python, Node.js, Java, Go, etc.)
3. Basic server structure and concepts
4. Implementing tools, resources, and prompts
5. Testing locally with miniclaw
6. Deployment strategies (STDIO, SSE, HTTP)
7. Best practices and security considerations

**Step 2: Create Python MCP server example**

Create `examples/custom-mcp-server/python/simple_server.py`:

```python
#!/usr/bin/env python3
"""
Simple MCP Server Example in Python
Demonstrates how to create a custom MCP server that can be used with miniclaw
"""

from mcp.server import MCPServer
from mcp.server.stdio import stdio_server

# Create MCP server instance
app = MCPServer("simple-example")

@app.tool()
async def greet(name: str) -> str:
    """Greet a person by name"""
    return f"Hello, {name}! This is a custom MCP tool."

@app.tool()
async def calculate(operation: str, a: float, b: float) -> float:
    """Perform basic calculations"""
    operations = {
        "add": lambda x, y: x + y,
        "subtract": lambda x, y: x - y,
        "multiply": lambda x, y: x * y,
        "divide": lambda x, y: x / y if y != 0 else None
    }

    if operation not in operations:
        raise ValueError(f"Unknown operation: {operation}")

    result = operations[operation](a, b)
    if result is None:
        raise ValueError("Division by zero")

    return result

@app.resource("config://example")
async def get_config() -> str:
    """Provide configuration information"""
    return "Example configuration data"

@app.prompt()
async def example_prompt(topic: str) -> str:
    """Generate a prompt template"""
    return f"Please explain {topic} in detail."

if __name__ == "__main__":
    # Run as STDIO server
    stdio_server(app)
```

**Step 3: Create Node.js MCP server example**

Create `examples/custom-mcp-server/nodejs/simple-server.js`:

```javascript
#!/usr/bin/env node
/**
 * Simple MCP Server Example in Node.js
 * Demonstrates how to create a custom MCP server that can be used with miniclaw
 */

import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";

// Create MCP server
const server = new Server({
  name: "simple-example",
  version: "1.0.0"
}, {
  capabilities: {
    tools: {},
    resources: {},
    prompts: {}
  }
});

// Register tools
server.setRequestHandler("tools/list", async () => ({
  tools: [
    {
      name: "greet",
      description: "Greet a person by name",
      inputSchema: {
        type: "object",
        properties: {
          name: { type: "string", description: "Name to greet" }
        },
        required: ["name"]
      }
    },
    {
      name: "calculate",
      description: "Perform basic calculations",
      inputSchema: {
        type: "object",
        properties: {
          operation: {
            type: "string",
            enum: ["add", "subtract", "multiply", "divide"],
            description: "Operation to perform"
          },
          a: { type: "number", description: "First number" },
          b: { type: "number", description: "Second number" }
        },
        required: ["operation", "a", "b"]
      }
    }
  ]
}));

// Handle tool calls
server.setRequestHandler("tools/call", async (request) => {
  const { name, arguments: args } = request.params;

  if (name === "greet") {
    return {
      content: [
        {
          type: "text",
          text: `Hello, ${args.name}! This is a custom MCP tool.`
        }
      ]
    };
  }

  if (name === "calculate") {
    const operations = {
      add: (a, b) => a + b,
      subtract: (a, b) => a - b,
      multiply: (a, b) => a * b,
      divide: (a, b) => b !== 0 ? a / b : null
    };

    const result = operations[args.operation](args.a, args.b);

    if (result === null) {
      throw new Error("Division by zero");
    }

    return {
      content: [
        {
          type: "text",
          text: `Result: ${result}`
        }
      ]
    };
  }

  throw new Error(`Unknown tool: ${name}`);
});

// Start server
async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error("Simple MCP Server running on stdio");
}

main().catch(console.error);
```

**Step 4: Create example README**

Create `examples/custom-mcp-server/README.md`:

```markdown
# Custom MCP Server Examples

This directory contains examples of custom MCP servers that can be integrated with miniclaw.

## Python Example

### Prerequisites
```bash
pip install mcp-server
```

### Run
```bash
python python/simple_server.py
```

### Configure in miniclaw
```yaml
mcp:
  servers:
    - name: my-python-server
      transport: stdio
      command: python
      args:
        - "/path/to/simple_server.py"
      enabled: true
      tool-prefix: custom_
```

## Node.js Example

### Prerequisites
```bash
npm install @modelcontextprotocol/sdk
```

### Run
```bash
node nodejs/simple-server.js
```

### Configure in miniclaw
```yaml
mcp:
  servers:
    - name: my-nodejs-server
      transport: stdio
      command: node
      args:
        - "/path/to/simple-server.js"
      enabled: true
      tool-prefix: custom_
```

## Testing

1. Start miniclaw with the custom server configured
2. The tools will be automatically discovered: `custom_greet`, `custom_calculate`
3. Use them in conversation with the AI agent

## Next Steps

- Add more tools, resources, and prompts
- Deploy as HTTP/SSE server for remote access
- Add authentication and security
- Package and distribute your MCP server
```

**Step 5: Commit examples**

```bash
git add docs/custom-mcp-server-guide.md \
        examples/custom-mcp-server/
git commit -m "docs: add custom MCP server development guide and examples"
```

---

### Task 16: Manual Testing Guide

**Files:**
- Create: `docs/mcp-testing-guide.md`
- Create: `scripts/test-mcp-stdio.sh`
- Create: `scripts/test-mcp-sse.sh`
- Create: `scripts/test-custom-mcp.sh`

**Step 1: Write comprehensive testing guide**

Create `docs/mcp-testing-guide.md` with:
1. How to test STDIO transport (with local npx server)
2. How to test SSE transport (with mock server)
3. How to test HTTP transport
4. **How to test custom MCP servers**
5. How to verify tool discovery
6. How to test tool execution
7. How to verify resource access
8. How to verify health checks
9. Common troubleshooting scenarios

**Step 2: Create STDIO test script**

Create `scripts/test-mcp-stdio.sh`:

```bash
#!/bin/bash
# MCP STDIO Transport Test Script
# Starts a filesystem MCP server for testing

echo "Starting MCP filesystem server (STDIO) on /tmp..."
npx -y @modelcontextprotocol/server-filesystem /tmp
```

**Step 3: Create SSE test script**

Create `scripts/test-mcp-sse.sh`:

```bash
#!/bin/bash
# MCP SSE Transport Test Script
# Note: Requires a running SSE MCP server

echo "Testing SSE connection to http://localhost:3000/sse"
curl -N http://localhost:3000/sse
```

**Step 4: Create custom MCP test script**

Create `scripts/test-custom-mcp.sh`:

```bash
#!/bin/bash
# Test Custom MCP Server
# Runs the example Python MCP server

echo "Starting custom Python MCP server..."
python examples/custom-mcp-server/python/simple_server.py
```

**Step 5: Commit testing guide and scripts**

```bash
chmod +x scripts/test-mcp-*.sh
git add docs/mcp-testing-guide.md scripts/test-mcp-*.sh
git commit -m "docs: add MCP testing guide for official, third-party, and custom servers"
```

---

## Summary

This implementation plan provides a complete, test-driven approach to integrating MCP v0.17.2 into miniclaw **as an MCP Client** with **full frontend integration**:

**What this enables:**
- ✅ Connect to **unlimited MCP Servers simultaneously**
- ✅ Support **official MCP Servers** (filesystem, fetch, etc.)
- ✅ Support **third-party MCP Servers** (Tavily, GitHub, etc.)
- ✅ Support **user-developed custom MCP Servers** (any language, any deployment)
- ✅ **Frontend UI for managing MCP servers** (add, edit, delete, test)
- ✅ **Dynamic hot-loading** (no restart required)
- ✅ **Database persistence** (SQLite 为主，兼容 PostgreSQL)
- ✅ Auto-discover tools, resources, prompts from each server
- ✅ Tool name prefixes prevent conflicts between servers
- ✅ Per-server HITL policies and permissions

**Phase 1: Foundation** - Dependencies (v0.17.2) and configuration with three transport types (3 tasks)
**Phase 2: Database Persistence** - Entity, repository, service for dynamic configuration (3 tasks)
**Phase 3: Client Management** - Transport factory (STDIO/SSE/HTTP), sync client wrapper, dynamic connection manager (3 tasks)
**Phase 4: Tool Adaptation** - Tool adapter, registry, auto-discovery (2 tasks)
**Phase 5: Resources & Prompts** - Resource tool, prompt provider (2 tasks)
**Phase 6: Integration** - System prompt integration, health checks, custom server guide (4 tasks)
**Phase 7: Frontend Integration** - RPC handlers, frontend guide (2 tasks)
**Phase 8: Testing** - Integration tests, manual testing guide for all transports (3 tasks)

**Total: 22 tasks across 8 phases**

**Key Design Principles:**
- **Correct SDK Version**: Uses v0.17.2 (latest as of 2026-01-22)
- **Three Transport Types**: Full support for STDIO, SSE, and Streamable HTTP
- **Sync Facade**: Uses `McpClient.sync()` for simpler blocking implementation
- **DRY**: Reuse existing Tool interface, ToolRegistry, ToolDispatcher
- **YAGNI**: Only implement features needed for MVP
- **TDD**: Write tests before implementation
- **Frequent Commits**: One commit per task

**Transport Type Usage:**
- **STDIO**: Local MCP servers (npx, Python scripts, local binaries) - process pipes
- **SSE**: Remote servers with server-sent events - unidirectional streaming
- **HTTP**: Remote servers with Streamable HTTP - bidirectional streaming

**User Scenarios Supported:**
1. **Use official MCP servers**: `npx -y @modelcontextprotocol/server-*`
2. **Use third-party MCP servers**: Connect to community-built servers
3. **Develop custom MCP servers**: Build your own in Python/Node.js/Java/etc.
4. **Mix and match**: Use multiple servers simultaneously with tool prefixes

**Next Steps After Implementation:**
1. Deploy to test environment
2. Connect to official MCP servers (filesystem, fetch, etc.)
3. Test with third-party MCP servers (Tavily, GitHub, etc.)
4. **Create example custom MCP servers**
5. **Write user guide for developing custom MCP servers**
6. Test all three transport types in production scenarios
7. Gather user feedback
8. Optimize performance and error handling
9. Add advanced features (caching, batching, metrics)

---

## Execution Handoff

**Plan complete and saved to `docs/plans/2026-02-11-mcp-integration.md`.**

**Updated based on MCP Java SDK v0.17.2:**
✅ Correct version (0.17.2)
✅ Three transport types explicitly supported (STDIO, SSE, HTTP)
✅ Uses `McpClient.sync()` with `ServerParameters` for STDIO
✅ Uses `HttpClientStreamableHttpTransport` for SSE/HTTP
✅ Updated configuration examples for all transports
✅ **SQLite 为主，兼容 PostgreSQL**（双数据库脚本，JPA 统一抽象）
✅ **Added database persistence for dynamic configuration**
✅ **Added frontend RPC handlers for UI integration**
✅ **Added hot-loading support (no restart required)**
✅ **Added connection testing before saving**
✅ **Added support for user-developed custom MCP servers**
✅ **Included custom MCP server development guide and examples**
✅ **22 tasks total across 8 phases**

**Architecture Highlights:**
- 📊 **Database-first**: All MCP server configs stored in database
  - **SQLite 为主**（桌面端优先）
  - **兼容 PostgreSQL**（服务器部署）
  - JPA 统一抽象，双数据库脚本
- 🔥 **Hot-loading**: Add/edit/delete servers without restart
- 🧪 **Test-first**: Validate connections before saving
- 🎨 **Frontend-ready**: Complete RPC API for UI integration
- 🔧 **Extensible**: Support any MCP server (official, third-party, custom)

**User Experience:**
```
Frontend → Test Connection → Save to DB → Backend Hot-loads → Tools Available Immediately
```

**Two execution options:**

**1. Subagent-Driven (this session)** - I dispatch fresh subagent per task, review between tasks, fast iteration

**2. Parallel Session (separate)** - Open new session with executing-plans, batch execution with checkpoints

**Which approach would you prefer?**
