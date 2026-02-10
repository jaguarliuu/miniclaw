# Node Module Security & Scalability Optimization Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复 node 模块的高危安全问题、稳定性问题，并为大规模节点巡检和多驱动扩展做好架构准备

**Architecture:**
- 第一阶段：修复 P0 高危问题（超时、OOM、安全策略、日志泄密、配置校验）
- 第二阶段：优化并发一致性、扩展接口设计，支持大规模巡检
- 第三阶段：建立可扩展架构，支持 K8s 和多种数据库驱动

**Tech Stack:**
- Spring Boot 3.x + JPA
- JSch (SSH), Kubernetes Java Client
- AES-256-GCM 加密
- SQLite/PostgreSQL

---

## 阶段一：立刻提升稳定性与安全（P0 高危问题）

### Task 1.1: SSH 执行硬超时机制

**问题：** `SshConnector.execute()` 只对 connect 阶段设置 timeout，读取输出用 `while(true)` 循环，没有总耗时截止，遇到 `tail -f`、网络阻塞会无限占用线程

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/nodeconsole/SshConnector.java`
- Modify: `src/main/java/com/jaguarliu/ai/nodeconsole/NodeConsoleProperties.java`
- Test: `src/test/java/com/jaguarliu/ai/nodeconsole/SshConnectorTimeoutTest.java`

**Step 1: 添加配置参数区分 SSH 连接超时和执行超时**

修改 `NodeConsoleProperties.java`:

```java
@ConfigurationProperties(prefix = "node-console")
public class NodeConsoleProperties {
    private String encryptionKey;
    private String defaultSafetyPolicy = "strict";

    // 新增：明确区分连接超时和执行超时
    private int sshConnectTimeoutSeconds = 10;  // SSH 连接超时
    private int execTimeoutSeconds = 60;        // 命令执行超时

    private int maxOutputLength = 32000;

    // getters and setters...
}
```

**Step 2: 编写超时测试用例**

创建 `src/test/java/com/jaguarliu/ai/nodeconsole/SshConnectorTimeoutTest.java`:

```java
package com.jaguarliu.ai.nodeconsole;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

class SshConnectorTimeoutTest {

    @Test
    void testExecutionHardTimeout() {
        // 使用 mock SSH server 或跳过
        // 测试命令执行超过 execTimeout 时能正确中断并返回超时错误
        SshConnector connector = new SshConnector();
        NodeEntity node = createTestNode();

        long startTime = System.currentTimeMillis();
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            // 执行一个会卡住的命令，超时时间设置为 2 秒
            connector.execute("password", node, "sleep 100", 2);
        });
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(exception.getMessage().contains("timeout") ||
                   exception.getMessage().contains("timed out"),
                   "Exception should indicate timeout");
        assertTrue(duration < 5000, "Should timeout within ~2 seconds (with margin)");
    }

    @Test
    void testConnectionTimeoutSeparate() {
        SshConnector connector = new SshConnector();
        NodeEntity node = new NodeEntity();
        node.setHost("192.0.2.1"); // TEST-NET-1, non-routable
        node.setPort(22);
        node.setUsername("test");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            connector.testConnection("password", node);
        });

        assertTrue(exception.getMessage().contains("timeout") ||
                   exception.getMessage().contains("connect"),
                   "Should fail with connection timeout");
    }

    private NodeEntity createTestNode() {
        NodeEntity node = new NodeEntity();
        node.setHost("localhost");
        node.setPort(22);
        node.setUsername("testuser");
        node.setAuthType("password");
        return node;
    }
}
```

**Step 3: 运行测试验证失败**

```bash
./mvnw test -Dtest=SshConnectorTimeoutTest
```

预期：测试失败，因为当前实现没有执行硬超时

**Step 4: 实现执行硬超时机制**

修改 `SshConnector.java`:

```java
package com.jaguarliu.ai.nodeconsole;

import com.jcraft.jsch.*;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.*;

@Component
public class SshConnector implements Connector {

    private static final int SSH_CONNECT_TIMEOUT_MS = 10000; // 10秒连接超时
    private static final int BUFFER_SIZE = 4096;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public String getType() {
        return "ssh";
    }

    @Override
    public String execute(String credential, NodeEntity node, String command, int timeoutSeconds) {
        // 使用 Future 实现硬超时
        Future<String> future = executor.submit(() -> executeInternal(credential, node, command));

        try {
            // 硬超时：如果超过 timeoutSeconds，抛出 TimeoutException
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true); // 尝试中断任务
            throw new RuntimeException("Command execution timed out after " + timeoutSeconds + " seconds");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Command execution interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException("Command execution failed", cause);
        }
    }

    private String executeInternal(String credential, NodeEntity node, String command) {
        Session session = null;
        ChannelExec channel = null;

        try {
            session = createSession(credential, node, SSH_CONNECT_TIMEOUT_MS);
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            channel.setOutputStream(stdout);
            channel.setErrStream(stderr);

            InputStream in = channel.getInputStream();
            InputStream err = channel.getExtInputStream();

            channel.connect(SSH_CONNECT_TIMEOUT_MS);

            // 读取输出（保持原有逻辑，但现在受外层 Future.get() 的硬超时控制）
            byte[] buffer = new byte[BUFFER_SIZE];
            while (true) {
                while (in.available() > 0) {
                    int read = in.read(buffer, 0, BUFFER_SIZE);
                    if (read < 0) break;
                    stdout.write(buffer, 0, read);
                }
                while (err.available() > 0) {
                    int read = err.read(buffer, 0, BUFFER_SIZE);
                    if (read < 0) break;
                    stderr.write(buffer, 0, read);
                }

                if (channel.isClosed()) {
                    if (in.available() > 0 || err.available() > 0) continue;
                    break;
                }

                // 检查线程中断状态（配合 Future.cancel）
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Execution interrupted by timeout");
                }

                Thread.sleep(100);
            }

            int exitCode = channel.getExitStatus();
            String output = stdout.toString("UTF-8");
            String errorOutput = stderr.toString("UTF-8");

            if (exitCode != 0 && !errorOutput.isEmpty()) {
                return "Exit code: " + exitCode + "\nStderr:\n" + errorOutput + "\nStdout:\n" + output;
            }
            return output;

        } catch (Exception e) {
            throw new RuntimeException("SSH command execution failed: " + e.getClass().getSimpleName(), e);
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    @Override
    public boolean testConnection(String credential, NodeEntity node) {
        Session session = null;
        try {
            session = createSession(credential, node, SSH_CONNECT_TIMEOUT_MS);
            return session.isConnected();
        } catch (Exception e) {
            return false;
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    private Session createSession(String credential, NodeEntity node, int timeoutMs) throws JSchException {
        JSch jsch = new JSch();

        String host = node.getHost() != null ? node.getHost() : "localhost";
        Integer port = node.getPort() != null ? node.getPort() : 22;
        String username = node.getUsername() != null ? node.getUsername() : "root";

        Session session = jsch.getSession(username, host, port);
        session.setConfig("StrictHostKeyChecking", "no");

        if ("key".equals(node.getAuthType())) {
            jsch.addIdentity("key", credential.getBytes(), null, null);
        } else {
            session.setPassword(credential);
        }

        session.connect(timeoutMs);
        return session;
    }
}
```

**Step 5: 运行测试验证通过**

```bash
./mvnw test -Dtest=SshConnectorTimeoutTest
```

预期：测试通过

**Step 6: 更新配置文件**

修改 `src/main/resources/application.yml`:

```yaml
node-console:
  encryption-key: ${NODE_CONSOLE_ENCRYPTION_KEY:}
  default-safety-policy: strict
  ssh-connect-timeout-seconds: 10  # SSH 连接超时
  exec-timeout-seconds: 60         # 命令执行超时
  max-output-length: 32000
```

**Step 7: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/nodeconsole/SshConnector.java \
        src/main/java/com/jaguarliu/ai/nodeconsole/NodeConsoleProperties.java \
        src/test/java/com/jaguarliu/ai/nodeconsole/SshConnectorTimeoutTest.java \
        src/main/resources/application.yml
git commit -m "fix(node): implement SSH execution hard timeout to prevent thread blocking

- Add ExecutorService with Future.get(timeout) for hard deadline enforcement
- Separate sshConnectTimeoutSeconds (10s) and execTimeoutSeconds (60s)
- Add thread interruption check in read loop
- Add comprehensive timeout tests
- Prevents infinite thread occupation from 'tail -f' or network hangs

Fixes: P0-1 from code review"
```

---

### Task 1.2: Connector 输出上限防止 OOM

**问题：** Connector 中把 stdout/stderr 全部读入 `ByteArrayOutputStream`，Service 才截断，大输出会在 Connector 内存暴涨导致 OOM

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/nodeconsole/ExecResult.java`
- Modify: `src/main/java/com/jaguarliu/ai/nodeconsole/Connector.java`
- Modify: `src/main/java/com/jaguarliu/ai/nodeconsole/SshConnector.java`
- Modify: `src/main/java/com/jaguarliu/ai/nodeconsole/K8sConnector.java`
- Modify: `src/main/java/com/jaguarliu/ai/nodeconsole/NodeService.java`
- Test: `src/test/java/com/jaguarliu/ai/nodeconsole/OutputTruncationTest.java`

**Step 1: 创建结构化返回对象 ExecResult**

创建 `src/main/java/com/jaguarliu/ai/nodeconsole/ExecResult.java`:

```java
package com.jaguarliu.ai.nodeconsole;

/**
 * 结构化命令执行结果
 */
public class ExecResult {
    private final String stdout;
    private final String stderr;
    private final int exitCode;
    private final boolean truncated;        // 输出是否被截断
    private final long originalLength;      // 原始输出长度（字节）
    private final boolean timedOut;         // 是否超时

    public ExecResult(String stdout, String stderr, int exitCode,
                      boolean truncated, long originalLength, boolean timedOut) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.exitCode = exitCode;
        this.truncated = truncated;
        this.originalLength = originalLength;
        this.timedOut = timedOut;
    }

    // Builder pattern for clarity
    public static class Builder {
        private String stdout = "";
        private String stderr = "";
        private int exitCode = 0;
        private boolean truncated = false;
        private long originalLength = 0;
        private boolean timedOut = false;

        public Builder stdout(String stdout) {
            this.stdout = stdout;
            return this;
        }

        public Builder stderr(String stderr) {
            this.stderr = stderr;
            return this;
        }

        public Builder exitCode(int exitCode) {
            this.exitCode = exitCode;
            return this;
        }

        public Builder truncated(boolean truncated) {
            this.truncated = truncated;
            return this;
        }

        public Builder originalLength(long originalLength) {
            this.originalLength = originalLength;
            return this;
        }

        public Builder timedOut(boolean timedOut) {
            this.timedOut = timedOut;
            return this;
        }

        public ExecResult build() {
            return new ExecResult(stdout, stderr, exitCode, truncated, originalLength, timedOut);
        }
    }

    // Getters
    public String getStdout() { return stdout; }
    public String getStderr() { return stderr; }
    public int getExitCode() { return exitCode; }
    public boolean isTruncated() { return truncated; }
    public long getOriginalLength() { return originalLength; }
    public boolean isTimedOut() { return timedOut; }

    public String formatOutput() {
        StringBuilder sb = new StringBuilder();
        if (!stdout.isEmpty()) {
            sb.append(stdout);
        }
        if (!stderr.isEmpty()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("Stderr:\n").append(stderr);
        }
        if (exitCode != 0) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("Exit code: ").append(exitCode);
        }
        if (truncated) {
            sb.append("\n[Output truncated at ").append(stdout.length() + stderr.length())
              .append(" chars, original was ").append(originalLength).append(" bytes]");
        }
        if (timedOut) {
            sb.append("\n[Execution timed out]");
        }
        return sb.toString();
    }
}
```

**Step 2: 编写输出截断测试**

创建 `src/test/java/com/jaguarliu/ai/nodeconsole/OutputTruncationTest.java`:

```java
package com.jaguarliu.ai.nodeconsole;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OutputTruncationTest {

    @Test
    void testLimitedOutputStreamTruncation() {
        int maxBytes = 100;
        LimitedByteArrayOutputStream los = new LimitedByteArrayOutputStream(maxBytes);

        byte[] data = new byte[200];
        for (int i = 0; i < 200; i++) {
            data[i] = (byte) ('A' + (i % 26));
        }

        los.write(data, 0, 200);

        assertTrue(los.isTruncated(), "Should be truncated");
        assertEquals(100, los.size(), "Should stop at max bytes");
        assertEquals(200, los.getOriginalLength(), "Should track original length");
    }

    @Test
    void testExecResultBuilder() {
        ExecResult result = new ExecResult.Builder()
            .stdout("output")
            .stderr("error")
            .exitCode(1)
            .truncated(true)
            .originalLength(50000)
            .build();

        assertTrue(result.isTruncated());
        assertEquals(50000, result.getOriginalLength());
        assertTrue(result.formatOutput().contains("truncated"));
    }
}
```

**Step 3: 运行测试验证失败**

```bash
./mvnw test -Dtest=OutputTruncationTest
```

预期：编译失败，因为 `LimitedByteArrayOutputStream` 还不存在

**Step 4: 实现 LimitedByteArrayOutputStream**

在 `SshConnector.java` 同包下创建内部类或独立类：

```java
package com.jaguarliu.ai.nodeconsole;

import java.io.ByteArrayOutputStream;

/**
 * 限制最大字节数的 ByteArrayOutputStream，防止 OOM
 */
class LimitedByteArrayOutputStream extends ByteArrayOutputStream {
    private final int maxBytes;
    private long originalLength = 0;
    private boolean truncated = false;

    public LimitedByteArrayOutputStream(int maxBytes) {
        super(Math.min(maxBytes, 8192)); // 初始容量
        this.maxBytes = maxBytes;
    }

    @Override
    public synchronized void write(int b) {
        originalLength++;
        if (count < maxBytes) {
            super.write(b);
        } else {
            truncated = true;
        }
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) {
        originalLength += len;
        int remainingSpace = maxBytes - count;

        if (remainingSpace > 0) {
            int bytesToWrite = Math.min(len, remainingSpace);
            super.write(b, off, bytesToWrite);

            if (bytesToWrite < len) {
                truncated = true;
            }
        } else {
            truncated = true;
        }
    }

    public boolean isTruncated() {
        return truncated;
    }

    public long getOriginalLength() {
        return originalLength;
    }
}
```

**Step 5: 更新 Connector 接口返回 ExecResult**

修改 `Connector.java`:

```java
package com.jaguarliu.ai.nodeconsole;

public interface Connector {
    String getType();

    /**
     * 执行命令并返回结构化结果
     * @param credential 解密后的凭据
     * @param node 节点信息
     * @param command 命令
     * @param timeoutSeconds 超时时间（秒）
     * @param maxOutputBytes 最大输出字节数（防止 OOM）
     * @return 结构化执行结果
     */
    ExecResult execute(String credential, NodeEntity node, String command,
                       int timeoutSeconds, int maxOutputBytes);

    boolean testConnection(String credential, NodeEntity node);
}
```

**Step 6: 更新 SshConnector 使用 LimitedByteArrayOutputStream**

修改 `SshConnector.java` 的 `executeInternal` 方法:

```java
private ExecResult executeInternal(String credential, NodeEntity node, String command, int maxOutputBytes) {
    Session session = null;
    ChannelExec channel = null;

    try {
        session = createSession(credential, node, SSH_CONNECT_TIMEOUT_MS);
        channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);

        // 使用限制输出的流
        LimitedByteArrayOutputStream stdout = new LimitedByteArrayOutputStream(maxOutputBytes);
        LimitedByteArrayOutputStream stderr = new LimitedByteArrayOutputStream(maxOutputBytes / 4); // stderr 更小

        channel.setOutputStream(stdout);
        channel.setErrStream(stderr);

        InputStream in = channel.getInputStream();
        InputStream err = channel.getExtInputStream();

        channel.connect(SSH_CONNECT_TIMEOUT_MS);

        // 读取输出（LimitedByteArrayOutputStream 会自动截断）
        byte[] buffer = new byte[BUFFER_SIZE];
        while (true) {
            while (in.available() > 0) {
                int read = in.read(buffer, 0, BUFFER_SIZE);
                if (read < 0) break;
                stdout.write(buffer, 0, read);
            }
            while (err.available() > 0) {
                int read = err.read(buffer, 0, BUFFER_SIZE);
                if (read < 0) break;
                stderr.write(buffer, 0, read);
            }

            if (channel.isClosed()) {
                if (in.available() > 0 || err.available() > 0) continue;
                break;
            }

            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Execution interrupted by timeout");
            }

            Thread.sleep(100);
        }

        int exitCode = channel.getExitStatus();
        boolean truncated = stdout.isTruncated() || stderr.isTruncated();
        long originalLength = stdout.getOriginalLength() + stderr.getOriginalLength();

        return new ExecResult.Builder()
            .stdout(stdout.toString("UTF-8"))
            .stderr(stderr.toString("UTF-8"))
            .exitCode(exitCode)
            .truncated(truncated)
            .originalLength(originalLength)
            .timedOut(false)
            .build();

    } catch (InterruptedException e) {
        // 超时中断
        return new ExecResult.Builder()
            .stderr("Execution interrupted by timeout")
            .exitCode(-1)
            .timedOut(true)
            .build();
    } catch (Exception e) {
        throw new RuntimeException("SSH command execution failed: " + e.getClass().getSimpleName(), e);
    } finally {
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }
}
```

更新 `execute` 方法签名:

```java
@Override
public ExecResult execute(String credential, NodeEntity node, String command,
                          int timeoutSeconds, int maxOutputBytes) {
    Future<ExecResult> future = executor.submit(() ->
        executeInternal(credential, node, command, maxOutputBytes));

    try {
        return future.get(timeoutSeconds, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
        future.cancel(true);
        return new ExecResult.Builder()
            .stderr("Command execution timed out after " + timeoutSeconds + " seconds")
            .exitCode(-1)
            .timedOut(true)
            .build();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return new ExecResult.Builder()
            .stderr("Command execution interrupted")
            .exitCode(-1)
            .build();
    } catch (ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException) {
            throw (RuntimeException) cause;
        }
        throw new RuntimeException("Command execution failed", cause);
    }
}
```

**Step 7: 更新 K8sConnector**

修改 `K8sConnector.java` 返回 `ExecResult`:

```java
@Override
public ExecResult execute(String credential, NodeEntity node, String command,
                          int timeoutSeconds, int maxOutputBytes) {
    try {
        // ... 现有 K8s 逻辑 ...
        String output = executeKubectlCommand(apiClient, command);

        // 检查是否超过输出限制
        boolean truncated = false;
        long originalLength = output.length();
        if (output.length() > maxOutputBytes) {
            output = output.substring(0, maxOutputBytes);
            truncated = true;
        }

        return new ExecResult.Builder()
            .stdout(output)
            .exitCode(0)
            .truncated(truncated)
            .originalLength(originalLength)
            .build();

    } catch (Exception e) {
        return new ExecResult.Builder()
            .stderr("K8s execution failed: " + e.getMessage())
            .exitCode(-1)
            .build();
    }
}
```

**Step 8: 更新 NodeService 使用 ExecResult**

修改 `NodeService.java`:

```java
public String executeCommand(String alias, String command) {
    NodeEntity node = findByAlias(alias)
        .orElseThrow(() -> new IllegalArgumentException("Node not found: " + alias));

    String credential = cipher.decrypt(node.getEncryptedCredential(), node.getCredentialIv());

    Connector connector = connectorFactory.get(node.getConnectorType());
    if (connector == null) {
        throw new IllegalStateException("Unsupported connector type: " + node.getConnectorType());
    }

    // 使用配置的超时和输出限制
    int timeout = properties.getExecTimeoutSeconds();
    int maxOutput = properties.getMaxOutputLength();

    ExecResult result = connector.execute(credential, node, command, timeout, maxOutput);

    // 格式化输出（包含截断/超时提示）
    return result.formatOutput();
}
```

**Step 9: 运行所有测试**

```bash
./mvnw test -Dtest=OutputTruncationTest
./mvnw test
```

预期：所有测试通过

**Step 10: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/nodeconsole/ExecResult.java \
        src/main/java/com/jaguarliu/ai/nodeconsole/LimitedByteArrayOutputStream.java \
        src/main/java/com/jaguarliu/ai/nodeconsole/Connector.java \
        src/main/java/com/jaguarliu/ai/nodeconsole/SshConnector.java \
        src/main/java/com/jaguarliu/ai/nodeconsole/K8sConnector.java \
        src/main/java/com/jaguarliu/ai/nodeconsole/NodeService.java \
        src/test/java/com/jaguarliu/ai/nodeconsole/OutputTruncationTest.java
git commit -m "fix(node): implement output size limit to prevent OOM

- Create ExecResult with truncated/originalLength/timedOut flags
- Implement LimitedByteArrayOutputStream with automatic truncation
- Update Connector interface to return ExecResult
- Apply output limit in Connector layer (not Service)
- Add comprehensive truncation tests
- Prevents memory explosion from large command outputs

Fixes: P0-2 from code review"
```

---


### Task 1.3: SafetyPolicyGuard 落地实现

**问题：** Node 有 `safetyPolicy` 字段，但 Service 只返回/保存，不检查命令，等于"远程任意命令执行"

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/nodeconsole/SafetyPolicyGuard.java`
- Modify: `src/main/java/com/jaguarliu/ai/nodeconsole/RemoteCommandClassifier.java`
- Test: `src/test/java/com/jaguarliu/ai/nodeconsole/SafetyPolicyGuardTest.java`

**Step 1: 编写策略测试**

创建 `src/test/java/com/jaguarliu/ai/nodeconsole/SafetyPolicyGuardTest.java`:

```java
package com.jaguarliu.ai.nodeconsole;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SafetyPolicyGuardTest {

    @Test
    void testStrictPolicyBlocksSideEffectCommands() {
        SafetyPolicyGuard guard = new SafetyPolicyGuard();
        RemoteCommandClassifier classifier = new RemoteCommandClassifier();

        // 只读命令在 strict 策略下应该需要 HITL
        var classification = classifier.classify("ls -la", "strict");
        assertTrue(classification.requiresHitl(), "Read-only commands need HITL in strict mode");

        // 副作用命令在 strict 策略下应该需要 HITL
        classification = classifier.classify("systemctl restart nginx", "strict");
        assertTrue(classification.requiresHitl(), "Side-effect commands need HITL in strict mode");
    }

    @Test
    void testStandardPolicyAllowsReadOnly() {
        RemoteCommandClassifier classifier = new RemoteCommandClassifier();

        // 只读命令在 standard 策略下应该自动执行
        var classification = classifier.classify("df -h", "standard");
        assertFalse(classification.requiresHitl(), "Read-only commands auto-execute in standard mode");

        // 副作用命令在 standard 策略下需要 HITL
        classification = classifier.classify("systemctl restart nginx", "standard");
        assertTrue(classification.requiresHitl(), "Side-effect commands need HITL in standard mode");
    }

    @Test
    void testRelaxedPolicyAllowsMostCommands() {
        RemoteCommandClassifier classifier = new RemoteCommandClassifier();

        // 只读命令自动执行
        var classification = classifier.classify("ps aux", "relaxed");
        assertFalse(classification.requiresHitl(), "Read-only commands auto-execute in relaxed mode");

        // 副作用命令也自动执行
        classification = classifier.classify("systemctl restart nginx", "relaxed");
        assertFalse(classification.requiresHitl(), "Side-effect commands auto-execute in relaxed mode");

        // 破坏性命令永远被拒绝
        classification = classifier.classify("rm -rf /", "relaxed");
        assertEquals(2, classification.level(), "Destructive commands blocked even in relaxed mode");
    }

    @Test
    void testDestructiveCommandsAlwaysBlocked() {
        RemoteCommandClassifier classifier = new RemoteCommandClassifier();

        String[] policies = {"strict", "standard", "relaxed"};
        String[] destructiveCommands = {
            "rm -rf /",
            "shutdown now",
            "kubectl delete namespace production",
            "DROP DATABASE users"
        };

        for (String policy : policies) {
            for (String command : destructiveCommands) {
                var classification = classifier.classify(command, policy);
                assertEquals(2, classification.level(),
                    "Destructive command should be level 2 in " + policy + " mode: " + command);
            }
        }
    }
}
```

**Step 2: 运行测试验证当前行为**

```bash
./mvnw test -Dtest=SafetyPolicyGuardTest
```

预期：部分测试失败，因为 `RemoteCommandClassifier` 还没有集成策略逻辑

**Step 3: 创建 SafetyPolicyGuard 类**

创建 `src/main/java/com/jaguarliu/ai/nodeconsole/SafetyPolicyGuard.java`:

```java
package com.jaguarliu.ai.nodeconsole;

import org.springframework.stereotype.Component;

/**
 * 安全策略守卫：根据节点策略和命令分类决定执行行为
 */
@Component
public class SafetyPolicyGuard {

    public enum Decision {
        AUTO_EXECUTE,    // 自动执行
        REQUIRE_HITL,    // 需要人工确认
        BLOCK            // 拒绝执行
    }

    /**
     * 根据安全级别和策略做出决策
     *
     * @param safetyLevel 命令安全级别（0=只读, 1=副作用, 2=破坏性）
     * @param safetyPolicy 节点安全策略（strict/standard/relaxed）
     * @return 执行决策
     */
    public Decision decide(int safetyLevel, String safetyPolicy) {
        // Level 2 (DESTRUCTIVE): 永远拒绝
        if (safetyLevel == 2) {
            return Decision.BLOCK;
        }

        // Level 1 (SIDE_EFFECT): 根据策略决定
        if (safetyLevel == 1) {
            if ("relaxed".equals(safetyPolicy)) {
                return Decision.AUTO_EXECUTE;
            }
            return Decision.REQUIRE_HITL;
        }

        // Level 0 (READ_ONLY): 根据策略决定
        if ("strict".equals(safetyPolicy)) {
            return Decision.REQUIRE_HITL;
        }
        return Decision.AUTO_EXECUTE;
    }

    /**
     * 检查命令是否可以执行（不包括 HITL 场景）
     */
    public boolean isAllowed(int safetyLevel, String safetyPolicy) {
        Decision decision = decide(safetyLevel, safetyPolicy);
        return decision != Decision.BLOCK;
    }

    /**
     * 检查是否需要 HITL 确认
     */
    public boolean requiresHitl(int safetyLevel, String safetyPolicy) {
        return decide(safetyLevel, safetyPolicy) == Decision.REQUIRE_HITL;
    }
}
```

**Step 4: 更新 RemoteCommandClassifier 集成策略**

修改 `RemoteCommandClassifier.java`，在 `Classification` 记录中添加 `requiresHitl` 方法:

```java
package com.jaguarliu.ai.nodeconsole;

import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

@Component
public class RemoteCommandClassifier {

    // ... 现有的模式定义 ...

    public record Classification(
        int level,
        String category,
        String reason,
        String policy
    ) {
        /**
         * 根据策略判断是否需要 HITL
         */
        public boolean requiresHitl() {
            // Level 2 永远拒绝（不是 HITL，是直接 BLOCK）
            if (level == 2) {
                return false; // 实际应该抛异常或有单独的 isBlocked() 方法
            }

            // Level 1 (SIDE_EFFECT): relaxed 自动执行，否则需要 HITL
            if (level == 1) {
                return !"relaxed".equals(policy);
            }

            // Level 0 (READ_ONLY): strict 需要 HITL，否则自动执行
            return "strict".equals(policy);
        }

        /**
         * 是否被策略阻止
         */
        public boolean isBlocked() {
            return level == 2; // DESTRUCTIVE 永远阻止
        }
    }

    public Classification classify(String command, String safetyPolicy) {
        if (command == null || command.isBlank()) {
            return new Classification(0, "empty", "Empty command", safetyPolicy);
        }

        String cmd = command.trim().toLowerCase();

        // Level 2: DESTRUCTIVE - 永远拒绝
        for (var pattern : DESTRUCTIVE_PATTERNS) {
            if (pattern.matcher(cmd).find()) {
                return new Classification(2, "destructive",
                    "Blocked: Destructive command pattern detected", safetyPolicy);
            }
        }

        // Level 0: READ_ONLY - 安全命令
        for (var pattern : READ_ONLY_PATTERNS) {
            if (pattern.matcher(cmd).find()) {
                return new Classification(0, "read_only",
                    "Read-only command", safetyPolicy);
            }
        }

        // Level 1: SIDE_EFFECT - 默认为有副作用
        return new Classification(1, "side_effect",
            "Command may have side effects", safetyPolicy);
    }

    // ... 现有的模式列表 ...
}
```

**Step 5: 更新工具层使用策略守卫**

修改 `RemoteExecTool.java`，集成 `SafetyPolicyGuard`:

```java
// 在 execute 方法中
Classification classification = classifier.classify(command, node.getSafetyPolicy());

// 检查是否被阻止
if (classification.isBlocked()) {
    auditLogService.logCommandExecution(
        "command.reject",
        alias, node.getId(), node.getConnectorType(), "remote_exec",
        command, "destructive", node.getSafetyPolicy(),
        false, null, "blocked",
        "Command blocked by safety policy: " + classification.reason(),
        0
    );
    return ToolResult.error("SECURITY_VIOLATION",
        "Command blocked: " + classification.reason());
}

// 检查是否需要 HITL
if (classification.requiresHitl()) {
    // 触发 HITL 逻辑...
}
```

**Step 6: 运行测试验证通过**

```bash
./mvnw test -Dtest=SafetyPolicyGuardTest
./mvnw test
```

预期：所有测试通过

**Step 7: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/nodeconsole/SafetyPolicyGuard.java \
        src/main/java/com/jaguarliu/ai/nodeconsole/RemoteCommandClassifier.java \
        src/main/java/com/jaguarliu/ai/tools/builtin/RemoteExecTool.java \
        src/test/java/com/jaguarliu/ai/nodeconsole/SafetyPolicyGuardTest.java
git commit -m "feat(node): implement SafetyPolicyGuard to enforce security policies

- Create SafetyPolicyGuard with strict/standard/relaxed policy logic
- Update RemoteCommandClassifier.Classification with requiresHitl()/isBlocked()
- Integrate policy guard in RemoteExecTool and KubectlExecTool
- Level 2 (destructive) always blocked regardless of policy
- Level 1 (side-effect) requires HITL except in relaxed mode
- Level 0 (read-only) requires HITL only in strict mode
- Add comprehensive policy enforcement tests

Fixes: P0-3 from code review"
```

---


### Task 1.4: 日志脱敏防止泄密

**问题：** `NodeService` 记录完整命令，SSH connector / handler 记录 `e.getMessage()`，可能包含 token、密钥、内网地址等敏感信息

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/nodeconsole/LogSanitizer.java`
- Modify: `src/main/java/com/jaguarliu/ai/nodeconsole/NodeService.java`
- Modify: `src/main/java/com/jaguarliu/ai/nodeconsole/SshConnector.java`
- Modify: `src/main/java/com/jaguarliu/ai/gateway/rpc/handler/NodeRegisterHandler.java`
- Test: `src/test/java/com/jaguarliu/ai/nodeconsole/LogSanitizerTest.java`

**Step 1-7:** （完整测试驱动开发流程，参见文档内容）

**Commit:**

```bash
git commit -m "security(node): implement log sanitization to prevent credential leakage

- Create LogSanitizer for command summary (length+hash only)
- Replace command logging with commandSummary() in NodeService
- Sanitize exception messages (only log class name, not message)
- Update RPC handlers to not expose internal errors to clients
- Prevents token/password/IP leakage in logs and RPC responses

Fixes: P0-4 from code review"
```

---

### Task 1.5: 移除危险默认值并强化校验

**问题：** `SshConnector.createSession` 对 host/username 做默认值 (localhost/root)，节点配置缺字段时会在网关本机执行命令

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/nodeconsole/NodeValidator.java`
- Modify: `src/main/java/com/jaguarliu/ai/nodeconsole/SshConnector.java`
- Modify: `src/main/java/com/jaguarliu/ai/nodeconsole/NodeService.java`
- Test: `src/test/java/com/jaguarliu/ai/nodeconsole/NodeValidationTest.java`

**Commit:**

```bash
git commit -m "security(node): remove dangerous defaults and add strict validation

- Create NodeValidator with connector-specific validation rules
- Remove localhost/root defaults from SshConnector
- Validate all required fields before save (host/port/username for SSH)
- Disallow localhost/127.0.0.1 and root username (security risks)
- Fail fast on misconfiguration instead of dangerous execution

Fixes: P0-5 from code review"
```

---

### Task 1.6: ConnectorFactory 防止重复类型覆盖

**问题：** `ConnectorFactory` 使用 `registry.put(type, connector)`，两个 Bean type 相同会静默覆盖

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/nodeconsole/ConnectorFactory.java`
- Test: `src/test/java/com/jaguarliu/ai/nodeconsole/ConnectorFactoryTest.java`

**Commit:**

```bash
git commit -m "fix(node): prevent duplicate connector type registration

- Use putIfAbsent() to detect duplicate connector types
- Fail fast at startup with clear error message
- Prevents silent override and undefined behavior

Fixes: P1-2 from code review"
```

---

## 阶段二：为大规模巡检做准备（P1 问题）

### Task 2.1: DB 层 alias 唯一索引

**问题：** `existsByAlias` + `save` 不是原子操作，并发注册同 alias 时一个会 DB 异常

**Files:**
- Create: `src/main/resources/db/migration-sqlite/V6__node_unique_alias.sql`
- Create: `src/main/resources/db/migration-postgresql/V6__node_unique_alias.sql`
- Modify: `src/main/java/com/jaguarliu/ai/nodeconsole/NodeService.java`
- Test: `src/test/java/com/jaguarliu/ai/nodeconsole/AliasUniquenessTest.java`

**Step 1: 创建 SQLite 迁移脚本**

创建 `src/main/resources/db/migration-sqlite/V6__node_unique_alias.sql`:

```sql
-- 确保 alias 唯一索引存在（V4 已创建，这里为幂等性再次检查）
-- SQLite 不支持 CREATE INDEX IF NOT EXISTS 在旧版本，因此使用 DROP + CREATE

-- 先删除旧索引（如果存在）
DROP INDEX IF EXISTS idx_nodes_alias;

-- 创建大小写不敏感的唯一索引
CREATE UNIQUE INDEX idx_nodes_alias_unique ON nodes(LOWER(alias));
```

**Step 2: 创建 PostgreSQL 迁移脚本**

创建 `src/main/resources/db/migration-postgresql/V6__node_unique_alias.sql`:

```sql
-- PostgreSQL: 确保 alias 唯一索引（大小写不敏感）
DROP INDEX IF EXISTS idx_nodes_alias;
CREATE UNIQUE INDEX idx_nodes_alias_unique ON nodes(LOWER(alias));
```

**Step 3: 更新 NodeService 捕获唯一冲突异常**

修改 `NodeService.java`:

```java
public NodeEntity register(/* ... */) {
    // ... 现有校验逻辑 ...

    try {
        return repository.save(node);
    } catch (DataIntegrityViolationException e) {
        // 捕获唯一约束冲突
        if (e.getMessage() != null && e.getMessage().toLowerCase().contains("alias")) {
            throw new IllegalArgumentException("Node alias already exists: " + alias);
        }
        throw e;
    }
}
```

**Step 4: 更新 RPC Handler 返回一致错误码**

修改 `NodeRegisterHandler.java`:

```java
try {
    NodeEntity node = nodeService.register(/* ... */);
    return RpcResponse.success(requestId, nodeService.toNodeDto(node));
} catch (IllegalArgumentException e) {
    if (e.getMessage().contains("already exists")) {
        return RpcResponse.error(requestId, "ALIAS_CONFLICT", e.getMessage());
    }
    return RpcResponse.error(requestId, "INVALID_ARGUMENT", e.getMessage());
}
```

**Step 5: 运行迁移和测试**

```bash
./mvnw flyway:migrate
./mvnw test
```

**Step 6: Commit**

```bash
git add src/main/resources/db/migration-sqlite/V6__node_unique_alias.sql \
        src/main/resources/db/migration-postgresql/V6__node_unique_alias.sql \
        src/main/java/com/jaguarliu/ai/nodeconsole/NodeService.java \
        src/main/java/com/jaguarliu/ai/gateway/rpc/handler/NodeRegisterHandler.java
git commit -m "fix(node): enforce alias uniqueness at DB layer

- Add unique index on LOWER(alias) for case-insensitive uniqueness
- Catch DataIntegrityViolationException in NodeService
- Return consistent ALIAS_CONFLICT RPC error code
- Prevents race condition in concurrent registrations

Fixes: P1-1 from code review"
```

---

### Task 2.2: ExecOptions 封装执行参数

**问题：** execute 只传 `timeoutSeconds`，无法传 maxOutput、工作目录、环境变量等，后续扩展会导致签名膨胀

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/nodeconsole/ExecOptions.java`
- Modify: `src/main/java/com/jaguarliu/ai/nodeconsole/Connector.java`
- Modify: `src/main/java/com/jaguarliu/ai/nodeconsole/SshConnector.java`
- Modify: `src/main/java/com/jaguarliu/ai/nodeconsole/K8sConnector.java`
- Modify: `src/main/java/com/jaguarliu/ai/nodeconsole/NodeService.java`
- Test: `src/test/java/com/jaguarliu/ai/nodeconsole/ExecOptionsTest.java`

**Step 1: 创建 ExecOptions 参数对象**

创建 `src/main/java/com/jaguarliu/ai/nodeconsole/ExecOptions.java`:

```java
package com.jaguarliu.ai.nodeconsole;

import java.util.Map;
import java.util.HashMap;

/**
 * 命令执行选项（封装参数，避免方法签名膨胀）
 */
public class ExecOptions {
    private final int timeoutSeconds;
    private final int maxOutputBytes;
    private final String workingDirectory;
    private final Map<String, String> environment;
    private final boolean dryRun;
    private final Map<String, String> labels;  // 用于审计/追踪

    private ExecOptions(Builder builder) {
        this.timeoutSeconds = builder.timeoutSeconds;
        this.maxOutputBytes = builder.maxOutputBytes;
        this.workingDirectory = builder.workingDirectory;
        this.environment = builder.environment;
        this.dryRun = builder.dryRun;
        this.labels = builder.labels;
    }

    public static class Builder {
        private int timeoutSeconds = 60;
        private int maxOutputBytes = 32000;
        private String workingDirectory = null;
        private Map<String, String> environment = new HashMap<>();
        private boolean dryRun = false;
        private Map<String, String> labels = new HashMap<>();

        public Builder timeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public Builder maxOutputBytes(int maxOutputBytes) {
            this.maxOutputBytes = maxOutputBytes;
            return this;
        }

        public Builder workingDirectory(String workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        public Builder environment(Map<String, String> environment) {
            this.environment = environment;
            return this;
        }

        public Builder dryRun(boolean dryRun) {
            this.dryRun = dryRun;
            return this;
        }

        public Builder labels(Map<String, String> labels) {
            this.labels = labels;
            return this;
        }

        public ExecOptions build() {
            return new ExecOptions(this);
        }
    }

    // Getters
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public int getMaxOutputBytes() { return maxOutputBytes; }
    public String getWorkingDirectory() { return workingDirectory; }
    public Map<String, String> getEnvironment() { return environment; }
    public boolean isDryRun() { return dryRun; }
    public Map<String, String> getLabels() { return labels; }
}
```

**Step 2: 更新 Connector 接口**

修改 `Connector.java`:

```java
public interface Connector {
    String getType();

    /**
     * 执行命令
     * @param credential 解密后的凭据
     * @param node 节点信息
     * @param command 命令
     * @param options 执行选项（超时、输出限制、环境变量等）
     * @return 结构化执行结果
     */
    ExecResult execute(String credential, NodeEntity node, String command, ExecOptions options);

    boolean testConnection(String credential, NodeEntity node);
}
```

**Step 3: 更新 SshConnector 和 K8sConnector**

修改实现类使用 `ExecOptions`:

```java
@Override
public ExecResult execute(String credential, NodeEntity node, String command, ExecOptions options) {
    Future<ExecResult> future = executor.submit(() ->
        executeInternal(credential, node, command, options));

    try {
        return future.get(options.getTimeoutSeconds(), TimeUnit.SECONDS);
    } catch (TimeoutException e) {
        // ...
    }
}

private ExecResult executeInternal(String credential, NodeEntity node, String command, ExecOptions options) {
    // 使用 options.getMaxOutputBytes() 等
}
```

**Step 4: 更新 NodeService**

修改 `NodeService.java`:

```java
public String executeCommand(String alias, String command) {
    // ...

    ExecOptions options = new ExecOptions.Builder()
        .timeoutSeconds(properties.getExecTimeoutSeconds())
        .maxOutputBytes(properties.getMaxOutputLength())
        .build();

    ExecResult result = connector.execute(credential, node, command, options);
    return result.formatOutput();
}
```

**Step 5: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/nodeconsole/ExecOptions.java \
        src/main/java/com/jaguarliu/ai/nodeconsole/Connector.java \
        src/main/java/com/jaguarliu/ai/nodeconsole/SshConnector.java \
        src/main/java/com/jaguarliu/ai/nodeconsole/K8sConnector.java \
        src/main/java/com/jaguarliu/ai/nodeconsole/NodeService.java
git commit -m "refactor(node): encapsulate execution parameters in ExecOptions

- Create ExecOptions with Builder pattern for flexibility
- Update Connector interface to accept ExecOptions
- Support workingDirectory, environment, dryRun, labels
- Prevents method signature explosion as features grow
- Improves testability and extensibility

Fixes: P1-4 from code review"
```

---

### Task 2.3: CredentialCipher 启动失败行为

**问题：** 未配置 key 时 init 只 warn，但 encrypt/decrypt 才抛异常，服务可启动但功能不可用

**Files:**
- Modify: `src/main/java/com/jaguarliu/ai/nodeconsole/CredentialCipher.java`
- Create: `src/main/java/com/jaguarliu/ai/nodeconsole/NodeConsoleHealthIndicator.java`
- Test: `src/test/java/com/jaguarliu/ai/nodeconsole/CredentialCipherTest.java`

**Step 1: 更新 CredentialCipher 启动即失败**

修改 `CredentialCipher.java`:

```java
@PostConstruct
public void init() {
    String keyHex = properties.getEncryptionKey();

    if (keyHex == null || keyHex.isBlank()) {
        throw new IllegalStateException(
            "NODE_CONSOLE_ENCRYPTION_KEY environment variable is required. " +
            "Generate a key with: openssl rand -hex 32"
        );
    }

    if (keyHex.length() != 64) {
        throw new IllegalStateException(
            "Encryption key must be 64 hex characters (32 bytes). " +
            "Current length: " + keyHex.length()
        );
    }

    try {
        this.key = new SecretKeySpec(hexToBytes(keyHex), "AES");
    } catch (Exception e) {
        throw new IllegalStateException("Failed to initialize encryption key: " + e.getMessage(), e);
    }
}
```

**Step 2: 添加 Health Indicator（可选，用于监控）**

创建 `src/main/java/com/jaguarliu/ai/nodeconsole/NodeConsoleHealthIndicator.java`:

```java
package com.jaguarliu.ai.nodeconsole;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class NodeConsoleHealthIndicator implements HealthIndicator {

    private final CredentialCipher cipher;

    public NodeConsoleHealthIndicator(CredentialCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public Health health() {
        try {
            // 测试加解密
            var encrypted = cipher.encrypt("health-check");
            String decrypted = cipher.decrypt(encrypted.ciphertext(), encrypted.iv());

            if ("health-check".equals(decrypted)) {
                return Health.up()
                    .withDetail("encryption", "operational")
                    .build();
            }

            return Health.down()
                .withDetail("encryption", "verification failed")
                .build();

        } catch (Exception e) {
            return Health.down()
                .withDetail("encryption", "error")
                .withDetail("error", e.getClass().getSimpleName())
                .build();
        }
    }
}
```

**Step 3: 更新文档**

在 `README.md` 或 `docs/configuration.md` 中添加:

```markdown
## Required Environment Variables

### NODE_CONSOLE_ENCRYPTION_KEY

**Required:** Yes
**Format:** 64 hex characters (32 bytes)
**Generation:** `openssl rand -hex 32`

Example:
```bash
export NODE_CONSOLE_ENCRYPTION_KEY="a1b2c3d4e5f6... (64 chars)"
```

**Important:** The application will fail to start if this is not set.
```

**Step 4: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/nodeconsole/CredentialCipher.java \
        src/main/java/com/jaguarliu/ai/nodeconsole/NodeConsoleHealthIndicator.java \
        docs/configuration.md
git commit -m "fix(node): fail fast on missing encryption key

- Throw IllegalStateException at startup if key not configured
- Add NodeConsoleHealthIndicator for encryption verification
- Update documentation with key generation instructions
- Prevents silent failure and confusing runtime errors

Fixes: P1-5 from code review"
```

---


## 阶段三：可扩展架构（K8s + 多种数据库驱动）

### Task 3.1: 凭据模型结构化

**问题：** 当前 credential 是一个字符串，对 K8s (kubeconfig) 和 DB (user/pass/jdbc) 不够结构化

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/nodeconsole/CredentialType.java`
- Create: `src/main/java/com/jaguarliu/ai/nodeconsole/CredentialPayload.java`
- Modify: `src/main/java/com/jaguarliu/ai/nodeconsole/NodeEntity.java`
- Modify: `src/main/java/com/jaguarliu/ai/nodeconsole/NodeService.java`
- Migration: `src/main/resources/db/migration-sqlite/V7__credential_type.sql`

**Step 1: 定义凭据类型枚举**

创建 `src/main/java/com/jaguarliu/ai/nodeconsole/CredentialType.java`:

```java
package com.jaguarliu.ai.nodeconsole;

public enum CredentialType {
    SSH_PASSWORD("ssh_password"),
    SSH_KEY("ssh_key"),
    KUBECONFIG("kubeconfig"),
    K8S_TOKEN("k8s_token"),
    DB_PASSWORD("db_password"),
    DB_CONNECTION_STRING("db_connection_string"),
    DB_WALLET("db_wallet");  // Oracle Wallet

    private final String value;

    CredentialType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static CredentialType fromValue(String value) {
        for (CredentialType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown credential type: " + value);
    }
}
```

**Step 2: 创建凭据 Payload 封装**

创建 `src/main/java/com/jaguarliu/ai/nodeconsole/CredentialPayload.java`:

```java
package com.jaguarliu.ai.nodeconsole;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * 凭据数据结构化封装（存储为 JSON）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CredentialPayload {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CredentialType type;
    private Map<String, Object> data;

    public CredentialPayload() {
        this.data = new HashMap<>();
    }

    public CredentialPayload(CredentialType type) {
        this.type = type;
        this.data = new HashMap<>();
    }

    // SSH Password
    public static CredentialPayload sshPassword(String password) {
        CredentialPayload payload = new CredentialPayload(CredentialType.SSH_PASSWORD);
        payload.data.put("password", password);
        return payload;
    }

    // SSH Key
    public static CredentialPayload sshKey(String privateKey, String passphrase) {
        CredentialPayload payload = new CredentialPayload(CredentialType.SSH_KEY);
        payload.data.put("privateKey", privateKey);
        if (passphrase != null) {
            payload.data.put("passphrase", passphrase);
        }
        return payload;
    }

    // Kubeconfig
    public static CredentialPayload kubeconfig(String content, String context) {
        CredentialPayload payload = new CredentialPayload(CredentialType.KUBECONFIG);
        payload.data.put("content", content);
        if (context != null) {
            payload.data.put("context", context);
        }
        return payload;
    }

    // DB Password
    public static CredentialPayload dbPassword(String username, String password, String database) {
        CredentialPayload payload = new CredentialPayload(CredentialType.DB_PASSWORD);
        payload.data.put("username", username);
        payload.data.put("password", password);
        if (database != null) {
            payload.data.put("database", database);
        }
        return payload;
    }

    // Serialization
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize credential payload", e);
        }
    }

    public static CredentialPayload fromJson(String json) {
        try {
            return MAPPER.readValue(json, CredentialPayload.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize credential payload", e);
        }
    }

    // Getters/Setters
    public CredentialType getType() { return type; }
    public void setType(CredentialType type) { this.type = type; }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }

    public String getString(String key) {
        return (String) data.get(key);
    }
}
```

**Step 3: 添加数据库迁移**

创建 `src/main/resources/db/migration-sqlite/V7__credential_type.sql`:

```sql
-- 添加凭据类型字段
ALTER TABLE nodes ADD COLUMN credential_type VARCHAR(30);

-- 添加凭据版本（用于轮换）
ALTER TABLE nodes ADD COLUMN credential_version INTEGER DEFAULT 1;

-- 根据现有 authType 迁移数据
UPDATE nodes SET credential_type = 'ssh_password' WHERE auth_type = 'password' AND connector_type = 'ssh';
UPDATE nodes SET credential_type = 'ssh_key' WHERE auth_type = 'key' AND connector_type = 'ssh';
UPDATE nodes SET credential_type = 'kubeconfig' WHERE connector_type = 'k8s';
```

**Step 4: 更新 NodeEntity**

修改 `NodeEntity.java`:

```java
@Entity
@Table(name = "nodes")
public class NodeEntity {
    // ... 现有字段 ...

    @Column(name = "credential_type")
    private String credentialType;  // 对应 CredentialType enum

    @Column(name = "credential_version")
    private Integer credentialVersion = 1;

    // ... getters/setters ...
}
```

**Step 5: 更新 NodeService 支持结构化凭据**

修改 `NodeService.java`:

```java
public NodeEntity register(/* ... */, String credentialJson) {
    // 解析并验证凭据 payload
    CredentialPayload payload = CredentialPayload.fromJson(credentialJson);

    // 根据 connectorType 校验 credentialType 匹配
    validateCredentialType(connectorType, payload.getType());

    // 加密整个 JSON payload
    var encrypted = cipher.encrypt(payload.toJson());

    node.setCredentialType(payload.getType().getValue());
    node.setEncryptedCredential(encrypted.ciphertext());
    node.setCredentialIv(encrypted.iv());

    // ...
}

private void validateCredentialType(String connectorType, CredentialType credType) {
    switch (connectorType) {
        case "ssh" -> {
            if (credType != CredentialType.SSH_PASSWORD && credType != CredentialType.SSH_KEY) {
                throw new IllegalArgumentException("SSH connector requires ssh_password or ssh_key credential");
            }
        }
        case "k8s" -> {
            if (credType != CredentialType.KUBECONFIG && credType != CredentialType.K8S_TOKEN) {
                throw new IllegalArgumentException("K8s connector requires kubeconfig or k8s_token credential");
            }
        }
    }
}
```

**Step 6: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/nodeconsole/CredentialType.java \
        src/main/java/com/jaguarliu/ai/nodeconsole/CredentialPayload.java \
        src/main/java/com/jaguarliu/ai/nodeconsole/NodeEntity.java \
        src/main/java/com/jaguarliu/ai/nodeconsole/NodeService.java \
        src/main/resources/db/migration-sqlite/V7__credential_type.sql
git commit -m "feat(node): add structured credential model

- Create CredentialType enum (ssh_password, ssh_key, kubeconfig, db_password, etc.)
- Create CredentialPayload with JSON serialization
- Add credential_type and credential_version columns
- Validate credential type matches connector type
- Prepares for K8s and database connectors

Addresses: CR recommendation 4.2"
```

---

### Task 3.2: 数据库 Connector 基础架构

**问题：** 需要支持 MySQL/PostgreSQL/Oracle 等多种数据库连接

**Files:**
- Create: `src/main/java/com/jaguarliu/ai/nodeconsole/db/DbConnector.java`
- Create: `src/main/java/com/jaguarliu/ai/nodeconsole/db/SqlPolicy.java`
- Create: `src/main/java/com/jaguarliu/ai/nodeconsole/db/SqlParser.java`
- Create: `src/main/java/com/jaguarliu/ai/tools/builtin/DbQueryTool.java`
- Test: `src/test/java/com/jaguarliu/ai/nodeconsole/db/SqlPolicyTest.java`

**Step 1: 创建 SQL 策略守卫**

创建 `src/main/java/com/jaguarliu/ai/nodeconsole/db/SqlPolicy.java`:

```java
package com.jaguarliu.ai.nodeconsole.db;

import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

/**
 * SQL 安全策略：限制 DDL/DML 操作
 */
@Component
public class SqlPolicy {

    // DDL 关键字（永远禁止）
    private static final Pattern DDL_PATTERN = Pattern.compile(
        "\\b(drop|create|alter|truncate|grant|revoke)\\b",
        Pattern.CASE_INSENSITIVE
    );

    // DML 写操作（根据策略决定）
    private static final Pattern DML_WRITE_PATTERN = Pattern.compile(
        "\\b(insert|update|delete|merge)\\b",
        Pattern.CASE_INSENSITIVE
    );

    public enum Decision {
        ALLOW,
        DENY,
        REQUIRE_HITL
    }

    /**
     * 检查 SQL 是否允许执行
     *
     * @param sql SQL 语句
     * @param safetyPolicy 安全策略 (strict/standard/relaxed)
     * @return 决策
     */
    public Decision check(String sql, String safetyPolicy) {
        if (sql == null || sql.isBlank()) {
            return Decision.DENY;
        }

        String normalizedSql = sql.trim().toLowerCase();

        // DDL 永远拒绝
        if (DDL_PATTERN.matcher(normalizedSql).find()) {
            return Decision.DENY;
        }

        // DML 写操作根据策略决定
        if (DML_WRITE_PATTERN.matcher(normalizedSql).find()) {
            return switch (safetyPolicy) {
                case "relaxed" -> Decision.REQUIRE_HITL;  // relaxed 也需要确认
                default -> Decision.DENY;
            };
        }

        // SELECT/SHOW/DESCRIBE 等只读操作
        return Decision.ALLOW;
    }

    /**
     * 自动添加 LIMIT 子句（防止大结果集）
     */
    public String addLimit(String sql, int maxRows) {
        String normalizedSql = sql.trim().toLowerCase();

        // 已有 LIMIT
        if (normalizedSql.contains("limit")) {
            return sql;
        }

        // 只对 SELECT 添加
        if (!normalizedSql.startsWith("select")) {
            return sql;
        }

        return sql + " LIMIT " + maxRows;
    }
}
```

**Step 2: 创建数据库 Connector**

创建 `src/main/java/com/jaguarliu/ai/nodeconsole/db/DbConnector.java`:

```java
package com.jaguarliu.ai.nodeconsole.db;

import com.jaguarliu.ai.nodeconsole.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库 Connector（支持 MySQL/PostgreSQL/Oracle 等）
 */
@Component
public class DbConnector implements Connector {

    private static final int DEFAULT_MAX_ROWS = 100;

    private final SqlPolicy sqlPolicy;

    public DbConnector(SqlPolicy sqlPolicy) {
        this.sqlPolicy = sqlPolicy;
    }

    @Override
    public String getType() {
        return "db";
    }

    @Override
    public ExecResult execute(String credential, NodeEntity node, String command, ExecOptions options) {
        try {
            // 解析凭据
            CredentialPayload payload = CredentialPayload.fromJson(credential);

            String username = payload.getString("username");
            String password = payload.getString("password");
            String database = payload.getString("database");

            // 构建 JDBC URL
            String jdbcUrl = buildJdbcUrl(node.getHost(), node.getPort(), database, node.getConnectorType());

            // 创建数据源
            DataSource dataSource = createDataSource(jdbcUrl, username, password);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.setQueryTimeout(options.getTimeoutSeconds());

            // 自动添加 LIMIT
            String sql = sqlPolicy.addLimit(command, DEFAULT_MAX_ROWS);

            // 执行查询
            List<String> results = new ArrayList<>();
            jdbc.query(sql, (ResultSet rs) -> {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();

                // 表头
                StringBuilder header = new StringBuilder();
                for (int i = 1; i <= columnCount; i++) {
                    header.append(meta.getColumnName(i));
                    if (i < columnCount) header.append("\t");
                }
                results.add(header.toString());

                // 数据行
                while (rs.next()) {
                    StringBuilder row = new StringBuilder();
                    for (int i = 1; i <= columnCount; i++) {
                        row.append(rs.getString(i));
                        if (i < columnCount) row.append("\t");
                    }
                    results.add(row.toString());

                    // 防止过多行
                    if (results.size() > DEFAULT_MAX_ROWS + 1) {
                        break;
                    }
                }
            });

            String output = String.join("\n", results);
            boolean truncated = results.size() > DEFAULT_MAX_ROWS + 1;

            return new ExecResult.Builder()
                .stdout(output)
                .exitCode(0)
                .truncated(truncated)
                .originalLength(output.length())
                .build();

        } catch (Exception e) {
            return new ExecResult.Builder()
                .stderr("Database query failed: " + e.getClass().getSimpleName())
                .exitCode(-1)
                .build();
        }
    }

    @Override
    public boolean testConnection(String credential, NodeEntity node) {
        try {
            CredentialPayload payload = CredentialPayload.fromJson(credential);
            String jdbcUrl = buildJdbcUrl(node.getHost(), node.getPort(),
                payload.getString("database"), node.getConnectorType());

            DataSource ds = createDataSource(jdbcUrl,
                payload.getString("username"),
                payload.getString("password"));

            JdbcTemplate jdbc = new JdbcTemplate(ds);
            jdbc.setQueryTimeout(5);
            jdbc.queryForObject("SELECT 1", Integer.class);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String buildJdbcUrl(String host, Integer port, String database, String dbType) {
        return switch (dbType) {
            case "mysql" -> String.format("jdbc:mysql://%s:%d/%s", host, port != null ? port : 3306, database);
            case "postgresql" -> String.format("jdbc:postgresql://%s:%d/%s", host, port != null ? port : 5432, database);
            case "oracle" -> String.format("jdbc:oracle:thin:@%s:%d:%s", host, port != null ? port : 1521, database);
            default -> throw new IllegalArgumentException("Unsupported database type: " + dbType);
        };
    }

    private DataSource createDataSource(String jdbcUrl, String username, String password) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(jdbcUrl);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }
}
```

**Step 3: 创建 DbQueryTool**

创建 `src/main/java/com/jaguarliu/ai/tools/builtin/DbQueryTool.java`:

```java
package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.nodeconsole.*;
import com.jaguarliu.ai.nodeconsole.db.SqlPolicy;
import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolResult;

/**
 * 数据库查询工具
 */
@Component
public class DbQueryTool implements Tool {

    private final NodeService nodeService;
    private final SqlPolicy sqlPolicy;
    private final AuditLogService auditLogService;

    // ... 构造函数 ...

    @Override
    public String getName() {
        return "db_query";
    }

    @Override
    public String getDescription() {
        return "Execute read-only SQL queries on registered database nodes. " +
               "DDL and DML write operations are blocked.";
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters) {
        String alias = (String) parameters.get("alias");
        String sql = (String) parameters.get("sql");

        // 查找节点
        NodeEntity node = nodeService.findByAlias(alias)
            .orElseThrow(() -> new IllegalArgumentException("Node not found: " + alias));

        // SQL 策略检查
        SqlPolicy.Decision decision = sqlPolicy.check(sql, node.getSafetyPolicy());

        if (decision == SqlPolicy.Decision.DENY) {
            auditLogService.logCommandExecution("command.reject", alias, node.getId(),
                node.getConnectorType(), "db_query", sql,
                "destructive", node.getSafetyPolicy(),
                false, null, "blocked",
                "SQL blocked by policy", 0);

            return ToolResult.error("SQL_BLOCKED",
                "This SQL statement is not allowed (DDL/DML write operations are prohibited)");
        }

        if (decision == SqlPolicy.Decision.REQUIRE_HITL) {
            // 触发 HITL...
        }

        // 执行查询
        String result = nodeService.executeCommand(alias, sql);
        return ToolResult.success(result);
    }
}
```

**Step 4: Commit**

```bash
git add src/main/java/com/jaguarliu/ai/nodeconsole/db/*.java \
        src/main/java/com/jaguarliu/ai/tools/builtin/DbQueryTool.java
git commit -m "feat(node): add database connector with SQL policy enforcement

- Create SqlPolicy to block DDL and DML write operations
- Implement DbConnector supporting MySQL/PostgreSQL/Oracle
- Auto-add LIMIT clause to prevent large result sets
- Create db_query tool for LLM SQL execution
- Enforce read-only mode by default

Addresses: CR recommendation 4.4"
```

---

## 验收标准

### 阶段一完成标准

- [ ] 所有 SSH 命令执行有硬超时（不超过 execTimeoutSeconds + 1秒）
- [ ] 输出大小限制在 Connector 层生效，防止 OOM
- [ ] SafetyPolicy 真正生效：strict/standard/relaxed 行为符合预期
- [ ] 日志中不包含完整命令、异常 message、凭据信息
- [ ] SSH 节点不允许 localhost/root 配置
- [ ] 重复 connector type 导致启动失败
- [ ] 所有测试通过，代码覆盖率 > 80%

### 阶段二完成标准

- [ ] 并发注册相同 alias 返回一致的 ALIAS_CONFLICT 错误
- [ ] Connector 接口使用 ExecOptions，支持扩展参数
- [ ] 缺少 NODE_CONSOLE_ENCRYPTION_KEY 时启动失败
- [ ] Health endpoint 显示加密状态

### 阶段三完成标准

- [ ] 凭据以 JSON 结构化存储（CredentialPayload）
- [ ] 支持 MySQL/PostgreSQL 数据库连接
- [ ] SQL 策略阻止 DDL/DML 写操作
- [ ] db_query 工具可用且受安全策略保护

---

## 附录：测试覆盖清单

### 单元测试

- [ ] `SshConnectorTimeoutTest` - 超时机制
- [ ] `OutputTruncationTest` - 输出截断
- [ ] `SafetyPolicyGuardTest` - 策略守卫
- [ ] `LogSanitizerTest` - 日志脱敏
- [ ] `NodeValidationTest` - 配置校验
- [ ] `ConnectorFactoryTest` - 工厂重复检测
- [ ] `SqlPolicyTest` - SQL 策略

### 集成测试

- [ ] `NodeServiceIntegrationTest` - 完整流程测试
- [ ] `DbConnectorIntegrationTest` - 数据库连接测试（需要 testcontainers）

---

## 实施注意事项

1. **每个 Task 独立提交**：便于回滚和代码审查
2. **测试先行**：每个任务先写测试，再实现
3. **渐进式部署**：第一阶段完成后可先部署到预生产环境
4. **监控埋点**：在关键路径添加 metrics（超时次数、OOM 次数、策略拒绝次数）
5. **文档更新**：每个阶段完成后更新 API 文档和运维手册

---

