# MCP Integration Test Guide

## 完成的功能

Phase 3 已完成 MCP 工具集成，包括：

1. **McpToolAdapter**: 将 MCP 工具适配为 miniclaw Tool 接口
2. **McpToolRegistry**: 自动发现并注册 MCP 工具
3. **McpClientManager**: 完整的连接生命周期管理

## 集成测试步骤

### 1. 准备测试 MCP 服务器

推荐使用官方的文件系统服务器进行测试：

```bash
# 安装 MCP 文件系统服务器（如果未安装）
npm install -g @modelcontextprotocol/server-filesystem
```

### 2. 在数据库中添加 MCP 服务器配置

有两种方式：

#### 方式 A: 直接通过 SQL 添加

```bash
sqlite3 ./miniclaw.db <<EOF
INSERT INTO mcp_servers (
    name,
    transport_type,
    command,
    args,
    enabled,
    tool_prefix,
    requires_hitl,
    request_timeout_seconds
) VALUES (
    'filesystem',                           -- 服务器名称
    'STDIO',                                -- 传输类型
    'npx',                                  -- 命令
    '["@modelcontextprotocol/server-filesystem", "/tmp"]',  -- 参数（JSON数组）
    1,                                      -- 启用
    'fs_',                                  -- 工具前缀
    0,                                      -- 不需要人工确认
    30                                      -- 超时时间（秒）
);
EOF
```

#### 方式 B: 通过 API 添加（需要实现前端或使用 curl）

```bash
# TODO: 等待 Phase 6 完成 API 端点后更新
```

### 3. 启动应用并验证连接

```bash
mvn spring-boot:run
```

查看日志，应该看到：

```
INFO c.j.ai.mcp.client.McpClientManager : Initializing MCP Client Manager
INFO c.j.ai.mcp.client.McpClientManager : Dynamically connecting to MCP server 'filesystem' via STDIO transport
INFO c.j.ai.mcp.client.ManagedMcpClient : Initializing MCP client: filesystem
INFO c.j.ai.mcp.client.ManagedMcpClient : MCP client initialized successfully: filesystem
INFO c.j.ai.mcp.client.McpClientManager : Successfully connected to MCP server: filesystem (transport: STDIO)
INFO c.j.ai.mcp.client.McpClientManager : MCP Client Manager initialized with 1 clients
INFO c.j.ai.mcp.tools.McpToolRegistry : Discovering and registering MCP tools
INFO c.j.ai.mcp.tools.McpToolRegistry : Discovering tools from MCP server: filesystem
INFO c.j.ai.mcp.tools.McpToolRegistry : Registered 3 tools from MCP server: filesystem
INFO c.j.ai.mcp.tools.McpToolRegistry : MCP tool discovery complete. Registered 3 tools from 1 clients
INFO com.jaguarliu.ai.tools.ToolRegistry : ToolRegistry initialized with 21 tools
```

### 4. 验证工具已注册

通过 API 或日志检查工具列表，应该包含：

- `fs_read_file` - 读取文件
- `fs_write_file` - 写入文件
- `fs_list_directory` - 列出目录

（具体工具取决于 MCP 服务器提供的功能）

### 5. 测试工具调用

#### 通过 LLM 对话测试

向 AI 发送请求：
```
请帮我读取 /tmp/test.txt 文件的内容
```

AI 应该能够：
1. 识别可用的 `fs_read_file` 工具
2. 调用该工具
3. 返回文件内容

#### 查看执行日志

```
INFO c.j.ai.mcp.tools.McpToolAdapter : Executing MCP tool: read_file with arguments: {path=/tmp/test.txt}
```

### 6. 测试 HITL (Human-in-the-Loop)

如果配置了 `requires_hitl=1` 或特定工具需要确认：

1. 更新数据库配置：
```sql
UPDATE mcp_servers
SET requires_hitl = 1
WHERE name = 'filesystem';
```

2. 或指定特定工具需要确认：
```sql
UPDATE mcp_servers
SET hitl_tools = '["write_file", "delete_file"]'
WHERE name = 'filesystem';
```

3. 重启应用，尝试调用这些工具，应该会提示需要人工确认

## 测试不同的传输类型

### STDIO 传输（本地进程）

```sql
INSERT INTO mcp_servers (name, transport_type, command, args, enabled)
VALUES ('local-server', 'STDIO', 'npx', '[@modelcontextprotocol/server-filesystem", "/tmp"]', 1);
```

### SSE 传输（Server-Sent Events）

```sql
INSERT INTO mcp_servers (name, transport_type, url, enabled)
VALUES ('remote-sse', 'SSE', 'http://localhost:3000/sse', 1);
```

### HTTP 传输（双向流）

```sql
INSERT INTO mcp_servers (name, transport_type, url, enabled)
VALUES ('remote-http', 'HTTP', 'http://localhost:3000', 1);
```

## 故障排查

### 连接失败

如果看到错误：
```
ERROR c.j.ai.mcp.client.McpClientManager : Failed to connect MCP server: xxx
```

检查：
1. 命令是否存在（`which npx`）
2. MCP 服务器是否已安装
3. 参数格式是否正确（JSON数组）
4. 工作目录权限

### 工具未注册

如果工具没有出现在 ToolRegistry 中：

1. 检查 MCP 客户端是否成功连接：
   ```
   INFO c.j.ai.mcp.client.McpClientManager : Successfully connected to MCP server
   ```

2. 检查工具发现日志：
   ```
   INFO c.j.ai.mcp.tools.McpToolRegistry : Registered X tools from MCP server
   ```

3. 如果 `Registered 0 tools`，可能是 MCP 服务器没有提供工具

### 超时错误

如果看到：
```
TimeoutException: Did not observe any item or terminal signal within 20000ms
```

增加超时时间：
```sql
UPDATE mcp_servers
SET request_timeout_seconds = 60
WHERE name = 'your-server';
```

## 清理测试数据

```bash
# 删除所有 MCP 服务器配置
sqlite3 ./miniclaw.db "DELETE FROM mcp_servers;"

# 或删除特定服务器
sqlite3 ./miniclaw.db "DELETE FROM mcp_servers WHERE name = 'filesystem';"
```

## 下一步

Phase 3 完成后，接下来的开发阶段：

- **Phase 4**: Resource & Prompt Support（资源和提示词支持）
- **Phase 5**: Integration & Polish（集成和完善）
- **Phase 6**: Frontend Integration（前端集成）
- **Phase 7**: Testing & Validation（测试和验证）

## 常用测试命令

```bash
# 查看当前配置的 MCP 服务器
sqlite3 ./miniclaw.db "SELECT * FROM mcp_servers;"

# 启用/禁用服务器
sqlite3 ./miniclaw.db "UPDATE mcp_servers SET enabled = 0 WHERE name = 'filesystem';"
sqlite3 ./miniclaw.db "UPDATE mcp_servers SET enabled = 1 WHERE name = 'filesystem';"

# 查看工具前缀
sqlite3 ./miniclaw.db "SELECT name, tool_prefix FROM mcp_servers;"

# 查看所有配置
sqlite3 ./miniclaw.db ".schema mcp_servers"
```
