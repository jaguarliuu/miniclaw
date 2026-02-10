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
    public ExecResult execute(String credential, NodeEntity node, String command,
                              int timeoutSeconds, int maxOutputBytes) {
        // 使用 Future 实现硬超时
        Future<ExecResult> future = executor.submit(() ->
            executeInternal(credential, node, command, maxOutputBytes));

        try {
            // 硬超时：如果超过 timeoutSeconds，抛出 TimeoutException
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true); // 尝试中断任务
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

    private ExecResult executeInternal(String credential, NodeEntity node, String command, int maxOutputBytes) {
    private ExecResult executeInternal(String credential, NodeEntity node, String command, int maxOutputBytes) {
        Session session = null;
        ChannelExec channel = null;
        try {
            session = createSession(credential, node, SSH_CONNECT_TIMEOUT_MS);
            session.connect(SSH_CONNECT_TIMEOUT_MS);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);

            // 使用限制输出的流
            LimitedByteArrayOutputStream stdout = new LimitedByteArrayOutputStream(maxOutputBytes);
            LimitedByteArrayOutputStream stderr = new LimitedByteArrayOutputStream(maxOutputBytes / 4); // stderr 更小

            channel.setOutputStream(stdout);
            channel.setErrStream(stderr);

            InputStream in = channel.getInputStream();
            InputStream err = channel.getExtInputStream();

            channel.connect(SSH_CONNECT_TIMEOUT_MS);

            // 读取输出（LimitedByteArrayOutputStream 会自动截断）
            byte[] buf = new byte[BUFFER_SIZE];
            int len;
            while (true) {
                while (in.available() > 0) {
                    len = in.read(buf);
                    if (len < 0) break;
                    stdout.write(buf, 0, len);
                }
                while (err.available() > 0) {
                    len = err.read(buf);
                    if (len < 0) break;
                    stderr.write(buf, 0, len);
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
            boolean truncated = stdout.isTruncated() || stderr.isTruncated();
            long originalLength = stdout.getOriginalLength() + stderr.getOriginalLength();

            return new ExecResult.Builder()
                .stdout(stdout.toString(StandardCharsets.UTF_8))
                .stderr(stderr.toString(StandardCharsets.UTF_8))
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
