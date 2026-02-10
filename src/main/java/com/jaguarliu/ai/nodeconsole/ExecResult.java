package com.jaguarliu.ai.nodeconsole;

/**
 * 结构化命令执行结果
 */
public class ExecResult {

    /**
     * 错误类型枚举（帮助客户端更好地处理不同类型的错误）
     */
    public enum ErrorType {
        NONE,                   // 成功，无错误
        TIMEOUT,                // 执行超时
        PERMISSION_DENIED,      // 权限不足
        NETWORK_ERROR,          // 网络错误（连接失败、超时等）
        AUTHENTICATION_FAILED,  // 认证失败（密码错误、密钥错误等）
        COMMAND_NOT_FOUND,      // 命令不存在
        VALIDATION_ERROR,       // 命令验证失败（不支持的操作等）
        RESOURCE_NOT_FOUND,     // 资源不存在（K8s pod/deployment 等）
        INTERNAL_ERROR,         // 内部错误
        UNKNOWN                 // 未知错误
    }

    private final String stdout;
    private final String stderr;
    private final int exitCode;
    private final boolean truncated;        // 输出是否被截断
    private final long originalLength;      // 原始输出长度（字节）
    private final boolean timedOut;         // 是否超时
    private final ErrorType errorType;      // 错误类型

    public ExecResult(String stdout, String stderr, int exitCode,
                      boolean truncated, long originalLength, boolean timedOut, ErrorType errorType) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.exitCode = exitCode;
        this.truncated = truncated;
        this.originalLength = originalLength;
        this.timedOut = timedOut;
        this.errorType = errorType != null ? errorType : ErrorType.NONE;
    }

    // Builder pattern for clarity
    public static class Builder {
        private String stdout = "";
        private String stderr = "";
        private int exitCode = 0;
        private boolean truncated = false;
        private long originalLength = 0;
        private boolean timedOut = false;
        private ErrorType errorType = ErrorType.NONE;

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
            if (timedOut) {
                this.errorType = ErrorType.TIMEOUT;
            }
            return this;
        }

        public Builder errorType(ErrorType errorType) {
            this.errorType = errorType;
            return this;
        }

        public ExecResult build() {
            return new ExecResult(stdout, stderr, exitCode, truncated, originalLength, timedOut, errorType);
        }
    }

    // Getters
    public String getStdout() { return stdout; }
    public String getStderr() { return stderr; }
    public int getExitCode() { return exitCode; }
    public boolean isTruncated() { return truncated; }
    public long getOriginalLength() { return originalLength; }
    public boolean isTimedOut() { return timedOut; }
    public ErrorType getErrorType() { return errorType; }

    public String formatOutput() {
        StringBuilder sb = new StringBuilder();
        if (!stdout.isEmpty()) {
            sb.append(stdout);
        }
        if (!stderr.isEmpty()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("[stderr]\n").append(stderr);
        }
        if (exitCode != 0) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("[exit code: ").append(exitCode).append("]");
        }
        if (truncated) {
            sb.append("\n[Output truncated at ").append(stdout.length() + stderr.length())
              .append(" chars, original was ").append(originalLength).append(" bytes]");
        }
        if (timedOut) {
            sb.append("\n[Execution timed out]");
        }
        // 添加错误类型信息（如果不是 NONE）
        if (errorType != ErrorType.NONE) {
            sb.append("\n[Error type: ").append(errorType).append("]");
        }
        return sb.toString();
    }
}
