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
        return sb.toString();
    }
}
