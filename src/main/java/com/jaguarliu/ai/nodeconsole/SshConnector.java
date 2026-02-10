package com.jaguarliu.ai.nodeconsole;

import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/**
 * SSH 连接器
 * 通过 JSch 执行远程 SSH 命令
 */
@Slf4j
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
    private String executeInternal(String credential, NodeEntity node, String command) {
        Session session = null;
        ChannelExec channel = null;
        try {
            session = createSession(credential, node, SSH_CONNECT_TIMEOUT_MS);
            session.connect(SSH_CONNECT_TIMEOUT_MS);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);

            ByteArrayOutputStream errStream = new ByteArrayOutputStream();
            channel.setErrStream(errStream);

            InputStream in = channel.getInputStream();
            channel.connect(SSH_CONNECT_TIMEOUT_MS);

            // 读取标准输出（保持原有逻辑，但现在受外层 Future.get() 的硬超时控制）
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            byte[] buf = new byte[BUFFER_SIZE];
            int len;
            while (true) {
                while (in.available() > 0) {
                    len = in.read(buf);
                    if (len < 0) break;
                    outStream.write(buf, 0, len);
                }
                if (channel.isClosed()) {
                    if (in.available() > 0) continue;
                    break;
                }

                // 检查线程中断状态（配合 Future.cancel）
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Execution interrupted by timeout");
                }

                Thread.sleep(100);
            }

            String stdout = outStream.toString(StandardCharsets.UTF_8);
            String stderr = errStream.toString(StandardCharsets.UTF_8);
            int exitCode = channel.getExitStatus();

            StringBuilder result = new StringBuilder();
            if (!stdout.isEmpty()) {
                result.append(stdout);
            }
            if (!stderr.isEmpty()) {
                if (!result.isEmpty()) result.append("\n");
                result.append("[stderr]\n").append(stderr);
            }
            result.append("\n[exit code: ").append(exitCode).append("]");

            return result.toString();
        } catch (Exception e) {
            log.error("SSH execute failed on node {}: {}", node.getAlias(), e.getClass().getSimpleName());
            throw new RuntimeException("SSH command execution failed: " + e.getClass().getSimpleName(), e);
        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
        }
    }

    @Override
    public boolean testConnection(String credential, NodeEntity node) {
        Session session = null;
        try {
            session = createSession(credential, node, SSH_CONNECT_TIMEOUT_MS);
            session.connect(SSH_CONNECT_TIMEOUT_MS);
            return session.isConnected();
        } catch (Exception e) {
            log.debug("SSH test connection failed for node {}: {}", node.getAlias(), e.getMessage());
            return false;
        } finally {
            if (session != null) session.disconnect();
        }
    }

    private Session createSession(String credential, NodeEntity node, int timeoutMs) throws JSchException {
        JSch jsch = new JSch();

        String authType = node.getAuthType() != null ? node.getAuthType() : "password";

        if ("key".equals(authType)) {
            // 凭据是私钥内容
            jsch.addIdentity("node-" + node.getAlias(), credential.getBytes(StandardCharsets.UTF_8), null, null);
        }

        String host = node.getHost() != null ? node.getHost() : "localhost";
        int port = node.getPort() != null ? node.getPort() : 22;
        String username = node.getUsername() != null ? node.getUsername() : "root";

        Session session = jsch.getSession(username, host, port);

        if ("password".equals(authType)) {
            session.setPassword(credential);
        }

        // 禁用严格主机密钥检查（运维场景）
        session.setConfig("StrictHostKeyChecking", "no");
        session.setTimeout(timeoutMs);

        return session;
    }
}
