package com.jaguarliu.ai.nodeconsole;

import java.util.HashMap;
import java.util.Map;

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
        this.environment = new HashMap<>(builder.environment);
        this.dryRun = builder.dryRun;
        this.labels = new HashMap<>(builder.labels);
    }

    public static Builder builder() {
        return new Builder();
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
            this.environment = new HashMap<>(environment);
            return this;
        }

        public Builder addEnvironment(String key, String value) {
            this.environment.put(key, value);
            return this;
        }

        public Builder dryRun(boolean dryRun) {
            this.dryRun = dryRun;
            return this;
        }

        public Builder labels(Map<String, String> labels) {
            this.labels = new HashMap<>(labels);
            return this;
        }

        public Builder addLabel(String key, String value) {
            this.labels.put(key, value);
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
    public Map<String, String> getEnvironment() { return new HashMap<>(environment); }
    public boolean isDryRun() { return dryRun; }
    public Map<String, String> getLabels() { return new HashMap<>(labels); }
}
