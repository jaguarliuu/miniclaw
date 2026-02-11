# MCP Integration Guide

## Overview

MiniClaw integrates with the **Model Context Protocol (MCP)** as a **client**, enabling dynamic connection to various MCP servers to extend its capabilities. This integration allows MiniClaw to:

- **Discover and execute tools** provided by MCP servers
- **Access resources** (files, data, API responses) through standardized interfaces
- **Utilize prompts** defined by MCP servers for specialized tasks
- **Connect to multiple servers** simultaneously with automatic tool prefixing to avoid naming conflicts

## What MCP Servers Can Be Connected?

MiniClaw can connect to any MCP-compliant server, including:

### 1. Official MCP Servers

Maintained by Anthropic and the MCP community:

- **Filesystem** (`@modelcontextprotocol/server-filesystem`) - File operations
- **Fetch** (`@modelcontextprotocol/server-fetch`) - HTTP requests
- **Git** (`@modelcontextprotocol/server-git`) - Git operations
- **Postgres** (`@modelcontextprotocol/server-postgres`) - PostgreSQL database access
- **Puppeteer** (`@modelcontextprotocol/server-puppeteer`) - Browser automation
- **Brave Search** - Web search capabilities
- **Google Drive** - Drive file access
- **Google Maps** - Location and mapping services
- **Memory** - Persistent key-value storage
- **Slack** - Slack integration
- **SQLite** - SQLite database access

### 2. Third-Party MCP Servers

Developed by the community:

- **Tavily Search** - Advanced web search
- **GitHub** - GitHub API access
- **AWS** - AWS service integration
- **Kubernetes** - K8s cluster management
- And many more...

### 3. User-Developed Custom MCP Servers

You can build your own MCP servers in:

- **Python** (using `mcp` Python SDK)
- **Node.js/TypeScript** (using `@modelcontextprotocol/sdk`)
- **Java** (using `mcp-java-sdk`)
- Any language that can implement the MCP protocol

**Use cases for custom servers:**
- Internal API access (CRM, ERP, databases)
- Proprietary data sources
- Custom business logic and workflows
- Machine learning model inference
- Legacy system integration

## Transport Types

MiniClaw supports three transport mechanisms for connecting to MCP servers:

### 1. STDIO (Standard Input/Output)

**Best for:** Local processes, command-line tools, official MCP servers

**How it works:**
- Spawns a child process
- Communicates via stdin/stdout using JSON-RPC
- Process lifecycle managed by MiniClaw

**Example:**
```yaml
mcp:
  servers:
    - name: filesystem
      transport: stdio
      command: npx
      args:
        - "-y"
        - "@modelcontextprotocol/server-filesystem"
        - "/tmp"
      enabled: true
```

**Advantages:**
- No network configuration required
- Automatic process lifecycle management
- Secure (no network exposure)

**Considerations:**
- Process must be available on the host machine
- Limited to local execution

### 2. SSE (Server-Sent Events)

**Best for:** Long-running services, event-driven servers, remote MCP servers

**How it works:**
- Connects to an HTTP endpoint that supports Server-Sent Events
- Receives messages as event streams
- Sends requests via HTTP POST

**Example:**
```yaml
mcp:
  servers:
    - name: tavily-search
      transport: sse
      url: http://localhost:3000/sse
      enabled: true
```

**Advantages:**
- Supports remote servers
- Efficient for long-running connections
- Server can push updates to client

**Considerations:**
- Requires SSE-capable server endpoint
- Network connectivity required

### 3. HTTP

**Best for:** REST-like services, enterprise APIs, containerized deployments

**How it works:**
- Sends JSON-RPC requests via HTTP POST
- Receives responses synchronously
- Standard request/response pattern

**Example:**
```yaml
mcp:
  servers:
    - name: my-api
      transport: http
      url: http://localhost:8080/mcp
      enabled: true
```

**Advantages:**
- Simple and widely supported
- Works with existing HTTP infrastructure
- Easy to deploy behind load balancers/proxies

**Considerations:**
- No server-initiated events
- May have higher latency than STDIO

## Configuration Examples

### Official MCP Server (Filesystem)

```yaml
mcp:
  servers:
    - name: filesystem
      transport: stdio
      command: npx
      args:
        - "-y"
        - "@modelcontextprotocol/server-filesystem"
        - "/tmp"
      enabled: true
      tool-prefix: fs_
      request-timeout-seconds: 30
```

### Third-Party MCP Server (SSE)

```yaml
mcp:
  servers:
    - name: tavily-search
      transport: sse
      url: http://localhost:3000/sse
      enabled: true
      tool-prefix: search_
      request-timeout-seconds: 60
```

### Custom Python MCP Server (STDIO)

```yaml
mcp:
  servers:
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
```

### Custom Node.js MCP Server (HTTP)

```yaml
mcp:
  servers:
    - name: my-nodejs-api
      transport: http
      url: http://localhost:8080/mcp
      enabled: true
      tool-prefix: api_
      hitl-tools: [delete_record, execute_sql]
      request-timeout-seconds: 45
```

### Custom Docker MCP Server (SSE)

```yaml
mcp:
  servers:
    - name: ml-inference
      transport: sse
      url: http://ml-container:9000/sse
      enabled: true
      tool-prefix: ml_
      request-timeout-seconds: 300
```

### Enterprise Internal MCP Server (HTTP)

```yaml
mcp:
  servers:
    - name: internal-crm
      transport: http
      url: https://crm.company.internal/mcp
      enabled: true
      tool-prefix: crm_
      requires-hitl: true
      request-timeout-seconds: 60
```

See `docs/examples/mcp-config-example.yml` for a complete configuration example.

## Tool Discovery and Usage

### Automatic Tool Discovery

When MiniClaw connects to an MCP server:

1. **Server Connection:** Establishes connection via configured transport
2. **Capability Discovery:** Calls `listTools()` to discover available tools
3. **Tool Registration:** Registers tools in the global ToolRegistry
4. **Prefix Application:** Applies `tool-prefix` to avoid naming conflicts

Example:
- Server provides: `read_file`, `write_file`, `list_directory`
- With `tool-prefix: fs_`
- Tools registered as: `fs_read_file`, `fs_write_file`, `fs_list_directory`

### Tool Execution

Tools from MCP servers are executed like built-in tools:

1. LLM requests tool execution (e.g., `fs_read_file`)
2. MiniClaw routes to McpToolAdapter
3. Adapter forwards request to MCP server
4. Server executes tool and returns result
5. Result passed back to LLM

### HITL (Human-in-the-Loop) Configuration

For sensitive operations, you can require user confirmation:

```yaml
mcp:
  servers:
    - name: filesystem
      # ... other config ...
      requires-hitl: false          # No confirmation for any tool
      hitl-tools: [delete_file]     # Only these tools require confirmation
```

**Options:**
- `requires-hitl: true` - All tools from this server require confirmation
- `requires-hitl: false` + `hitl-tools: [...]` - Only specified tools require confirmation
- `requires-hitl: false` + no `hitl-tools` - No tools require confirmation (default)

## Resource Access

MCP servers can expose **resources** - structured data like files, database records, or API responses.

### How Resources Work

1. **Discovery:** MiniClaw calls `listResources()` when connecting to server
2. **Registration:** If resources are available, registers `<prefix>resource_read` tool
3. **Access:** LLM can read resources by URI

Example:
```yaml
Tool: fs_resource_read(uri="file:///tmp/config.json")
Result: {contents of config.json}
```

### Resource URIs

Resources are identified by URIs:
- `file:///path/to/file` - File resources
- `db://table/record/123` - Database records
- `api://endpoint/data` - API responses
- Custom URI schemes defined by your MCP server

## Prompt Integration

MCP servers can provide **prompts** - predefined prompt templates for specialized tasks.

### How Prompts Work

1. **Discovery:** MiniClaw calls `listPrompts()` when connecting to server
2. **System Prompt Integration:** Prompts are added to the system prompt in FULL mode
3. **Visibility:** LLM sees available prompts and their descriptions

### Example

An MCP server provides:
```
- analyze_code: Perform static code analysis
- generate_tests: Generate unit tests for code
```

These appear in the system prompt as:
```
## MCP Server Capabilities

### code-tools
- **analyze_code**: Perform static code analysis
- **generate_tests**: Generate unit tests for code
```

The LLM can then use these prompts when appropriate.

## Health Checking and Auto-Reconnect

MiniClaw includes automatic health checking for MCP servers:

### Health Check Configuration

```yaml
mcp:
  health-check:
    interval-seconds: 60          # Check every 60 seconds
    max-retries: 3                # Retry failed connections 3 times
    retry-backoff-seconds: 5      # Wait 5 seconds between retries
```

### How It Works

1. **Scheduled Checks:** Every `interval-seconds`, sends `ping()` to each connected server
2. **Failure Detection:** If ping fails, marks client as disconnected
3. **Auto-Reconnect:** Attempts to reconnect disconnected clients
4. **Logging:** Logs all health check results and reconnection attempts

## Developing Custom MCP Servers

### Quick Start with Python

1. **Install SDK:**
```bash
pip install mcp
```

2. **Create Server:**
```python
from mcp.server import Server
from mcp.server.stdio import stdio_server
from mcp.types import Tool, TextContent

# Create server instance
server = Server("my-custom-server")

# Define a tool
@server.list_tools()
async def list_tools():
    return [
        Tool(
            name="calculate",
            description="Perform mathematical calculations",
            inputSchema={
                "type": "object",
                "properties": {
                    "expression": {"type": "string"}
                },
                "required": ["expression"]
            }
        )
    ]

# Implement tool execution
@server.call_tool()
async def call_tool(name: str, arguments: dict):
    if name == "calculate":
        # NOTE: This is a simplified example for demonstration only
        # In production, use a safe mathematical expression parser
        # like numexpr, asteval, or py-expression-eval
        # DO NOT use eval() in production - it's a security risk!
        import ast
        import operator

        # Simple safe evaluator (supports basic arithmetic only)
        def safe_eval(expr):
            ops = {
                ast.Add: operator.add,
                ast.Sub: operator.sub,
                ast.Mult: operator.mul,
                ast.Div: operator.truediv,
            }
            node = ast.parse(expr, mode='eval').body
            return _eval(node, ops)

        def _eval(node, ops):
            if isinstance(node, ast.Num):
                return node.n
            elif isinstance(node, ast.BinOp):
                return ops[type(node.op)](_eval(node.left, ops), _eval(node.right, ops))
            else:
                raise ValueError("Unsupported operation")

        result = safe_eval(arguments["expression"])
        return [TextContent(type="text", text=str(result))]
    raise ValueError(f"Unknown tool: {name}")

# Run server
if __name__ == "__main__":
    import asyncio
    asyncio.run(stdio_server(server))
```

3. **Test with MiniClaw:**
```yaml
mcp:
  servers:
    - name: my-calculator
      transport: stdio
      command: python
      args: ["/path/to/calculator_server.py"]
      enabled: true
```

### Quick Start with Node.js

1. **Install SDK:**
```bash
npm install @modelcontextprotocol/sdk
```

2. **Create Server:**
```typescript
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";

const server = new Server(
  {
    name: "my-custom-server",
    version: "1.0.0",
  },
  {
    capabilities: {
      tools: {},
    },
  }
);

// List available tools
server.setRequestHandler("tools/list", async () => {
  return {
    tools: [
      {
        name: "calculate",
        description: "Perform mathematical calculations",
        inputSchema: {
          type: "object",
          properties: {
            expression: { type: "string" },
          },
          required: ["expression"],
        },
      },
    ],
  };
});

// Execute tool
server.setRequestHandler("tools/call", async (request) => {
  if (request.params.name === "calculate") {
    // NOTE: This is a simplified example for demonstration only
    // In production, use a safe expression parser like math.js or expr-eval
    // DO NOT use eval() in production - it's a security risk!
    const mathjs = require('mathjs');
    const result = mathjs.evaluate(request.params.arguments.expression);

    return {
      content: [
        {
          type: "text",
          text: String(result),
        },
      ],
    };
  }
  throw new Error(`Unknown tool: ${request.params.name}`);
});

// Start server
const transport = new StdioServerTransport();
await server.connect(transport);
```

3. **Test with MiniClaw:**
```yaml
mcp:
  servers:
    - name: my-calculator
      transport: stdio
      command: node
      args: ["/path/to/calculator-server.js"]
      enabled: true
```

### Implementing Resources

**Python:**
```python
@server.list_resources()
async def list_resources():
    return [
        Resource(
            uri="data://users/list",
            name="User List",
            mimeType="application/json"
        )
    ]

@server.read_resource()
async def read_resource(uri: str):
    if uri == "data://users/list":
        users = fetch_users_from_db()
        return [TextContent(type="text", text=json.dumps(users))]
```

**Node.js:**
```typescript
server.setRequestHandler("resources/list", async () => {
  return {
    resources: [
      {
        uri: "data://users/list",
        name: "User List",
        mimeType: "application/json",
      },
    ],
  };
});

server.setRequestHandler("resources/read", async (request) => {
  if (request.params.uri === "data://users/list") {
    const users = await fetchUsersFromDb();
    return {
      contents: [
        {
          uri: request.params.uri,
          mimeType: "application/json",
          text: JSON.stringify(users),
        },
      ],
    };
  }
});
```

### Implementing Prompts

**Python:**
```python
@server.list_prompts()
async def list_prompts():
    return [
        Prompt(
            name="analyze_code",
            description="Perform static code analysis",
            arguments=[
                PromptArgument(
                    name="language",
                    description="Programming language",
                    required=True
                )
            ]
        )
    ]

@server.get_prompt()
async def get_prompt(name: str, arguments: dict):
    if name == "analyze_code":
        return GetPromptResult(
            messages=[
                PromptMessage(
                    role="user",
                    content=TextContent(
                        type="text",
                        text=f"Analyze this {arguments['language']} code for issues..."
                    )
                )
            ]
        )
```

**Node.js:**
```typescript
server.setRequestHandler("prompts/list", async () => {
  return {
    prompts: [
      {
        name: "analyze_code",
        description: "Perform static code analysis",
        arguments: [
          {
            name: "language",
            description: "Programming language",
            required: true,
          },
        ],
      },
    ],
  };
});

server.setRequestHandler("prompts/get", async (request) => {
  if (request.params.name === "analyze_code") {
    return {
      messages: [
        {
          role: "user",
          content: {
            type: "text",
            text: `Analyze this ${request.params.arguments.language} code for issues...`,
          },
        },
      ],
    };
  }
});
```

### Testing Locally

1. **Start your MCP server** (ensure it's working standalone)
2. **Configure in MiniClaw** (use `application-dev.yml` or environment-specific config)
3. **Start MiniClaw** and check logs for connection success
4. **Test tool discovery:**
   ```bash
   # Via RPC if you have the frontend
   # Or check application logs for registered tools
   ```
5. **Test tool execution** through chat interface

### Deployment Options

#### Docker

```dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt
COPY . .
CMD ["python", "mcp_server.py"]
```

Configure in MiniClaw:
```yaml
mcp:
  servers:
    - name: my-server
      transport: http
      url: http://my-server-container:8080/mcp
      enabled: true
```

#### Systemd Service

```ini
[Unit]
Description=My MCP Server
After=network.target

[Service]
Type=simple
User=mcpuser
WorkingDirectory=/opt/mcp-server
ExecStart=/usr/bin/python3 /opt/mcp-server/server.py
Restart=always

[Install]
WantedBy=multi-user.target
```

#### Cloud Deployment

- **AWS Lambda** - Serverless MCP server (HTTP transport)
- **Google Cloud Run** - Container-based MCP server (HTTP/SSE)
- **Azure Container Instances** - Container-based MCP server
- **Kubernetes** - Scalable MCP server deployment

## Troubleshooting

### Connection Issues

**Problem:** Server fails to connect

**Solutions:**
1. Check server is running and accessible
2. Verify transport configuration (STDIO: command exists, HTTP/SSE: URL is reachable)
3. Check logs for detailed error messages
4. For STDIO: Ensure command has execute permissions
5. For HTTP/SSE: Test endpoint with curl

**Example debug:**
```bash
# Test STDIO server manually
npx -y @modelcontextprotocol/server-filesystem /tmp

# Test HTTP/SSE endpoint
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"ping","params":{}}'
```

### Tool Discovery Failures

**Problem:** Tools not appearing in MiniClaw

**Solutions:**
1. Check server implements `listTools()` correctly
2. Verify server is connected (check logs)
3. Check for naming conflicts (use unique `tool-prefix`)
4. Restart MiniClaw to force re-discovery
5. Check health check logs for connection status

### Transport-Specific Problems

#### STDIO Issues

- **Problem:** Process crashes immediately
  - **Solution:** Run command manually to see error output
  - Check working directory and environment variables

- **Problem:** Timeout during connection
  - **Solution:** Increase `request-timeout-seconds`
  - Check process startup time

#### SSE Issues

- **Problem:** Connection drops frequently
  - **Solution:** Ensure server supports long-lived SSE connections
  - Check network stability and proxies

- **Problem:** No events received
  - **Solution:** Verify server sends SSE events with correct format
  - Check Content-Type is `text/event-stream`

#### HTTP Issues

- **Problem:** Slow responses
  - **Solution:** Increase `request-timeout-seconds`
  - Optimize server-side processing

- **Problem:** Connection refused
  - **Solution:** Check firewall rules
  - Verify server is listening on correct port

### Performance Issues

**Problem:** Slow tool execution

**Solutions:**
1. Increase `request-timeout-seconds` for slow operations
2. Optimize MCP server implementation
3. Use connection pooling for HTTP transport
4. Consider caching for frequently accessed resources

### Health Check Issues

**Problem:** Constant reconnection attempts

**Solutions:**
1. Check server stability
2. Increase `interval-seconds` to reduce check frequency
3. Review server logs for errors during ping
4. Verify network stability

## Additional Resources

- **MCP Specification:** https://spec.modelcontextprotocol.io/
- **Official MCP Servers:** https://github.com/modelcontextprotocol/servers
- **MCP Python SDK:** https://github.com/modelcontextprotocol/python-sdk
- **MCP TypeScript SDK:** https://github.com/modelcontextprotocol/typescript-sdk
- **MCP Java SDK:** https://github.com/modelcontextprotocol/java-sdk

## FAQ

**Q: Can I connect to multiple MCP servers at once?**
A: Yes! Use `tool-prefix` to avoid naming conflicts between servers.

**Q: Can I disable a server temporarily?**
A: Yes, set `enabled: false` in the configuration.

**Q: How do I update server configuration?**
A: Modify configuration file and restart MiniClaw. (Note: Future versions will support hot-reload)

**Q: Can I build an MCP server in Java?**
A: Yes! Use the `mcp-java-sdk` library. The integration process is the same.

**Q: Do MCP servers need to be on the same machine?**
A: No. HTTP and SSE transports support remote servers. Only STDIO requires local execution.

**Q: How secure is MCP integration?**
A: MCP servers run as separate processes with their own permissions. Use HITL configuration for sensitive operations. Always validate server sources and review tool capabilities.

**Q: Can I contribute my MCP server to the community?**
A: Absolutely! Share your server on GitHub with the `mcp-server` topic tag.
