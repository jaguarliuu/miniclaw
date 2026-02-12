package com.jaguarliu.ai.system;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 系统信息和环境检测服务
 */
@Slf4j
@Service
public class SystemInfoService {

    /**
     * 获取系统信息
     */
    public SystemInfo getSystemInfo() {
        return SystemInfo.builder()
                .os(System.getProperty("os.name"))
                .osVersion(System.getProperty("os.version"))
                .architecture(System.getProperty("os.arch"))
                .javaVersion(System.getProperty("java.version"))
                .javaVendor(System.getProperty("java.vendor"))
                .userHome(System.getProperty("user.home"))
                .userName(System.getProperty("user.name"))
                .totalMemory(Runtime.getRuntime().totalMemory())
                .freeMemory(Runtime.getRuntime().freeMemory())
                .maxMemory(Runtime.getRuntime().maxMemory())
                .availableProcessors(Runtime.getRuntime().availableProcessors())
                .build();
    }

    /**
     * 检测环境
     */
    public List<EnvironmentCheck> checkEnvironments() {
        List<EnvironmentCheck> checks = new ArrayList<>();

        checks.add(checkPython());
        checks.add(checkNode());
        checks.add(checkGit());

        return checks;
    }

    /**
     * 检测 Python
     */
    private EnvironmentCheck checkPython() {
        EnvironmentCheck check = EnvironmentCheck.builder()
                .name("Python")
                .command("python")
                .build();

        try {
            // 尝试 python3 命令
            String version = executeCommand("python3 --version");
            if (version != null && !version.isEmpty()) {
                check.setInstalled(true);
                check.setVersion(version.replace("Python ", "").trim());
                check.setPath(executeCommand(isWindows() ? "where python3" : "which python3"));
                return check;
            }

            // 尝试 python 命令
            version = executeCommand("python --version");
            if (version != null && !version.isEmpty()) {
                check.setInstalled(true);
                check.setVersion(version.replace("Python ", "").trim());
                check.setPath(executeCommand(isWindows() ? "where python" : "which python"));
                return check;
            }

            check.setInstalled(false);
        } catch (Exception e) {
            log.debug("Python not found: {}", e.getMessage());
            check.setInstalled(false);
        }

        return check;
    }

    /**
     * 检测 Node.js
     */
    private EnvironmentCheck checkNode() {
        EnvironmentCheck check = EnvironmentCheck.builder()
                .name("Node.js")
                .command("node")
                .build();

        try {
            String version = executeCommand("node --version");
            if (version != null && !version.isEmpty()) {
                check.setInstalled(true);
                check.setVersion(version.replace("v", "").trim());
                check.setPath(executeCommand(isWindows() ? "where node" : "which node"));
            } else {
                check.setInstalled(false);
            }
        } catch (Exception e) {
            log.debug("Node.js not found: {}", e.getMessage());
            check.setInstalled(false);
        }

        return check;
    }

    /**
     * 检测 Git
     */
    private EnvironmentCheck checkGit() {
        EnvironmentCheck check = EnvironmentCheck.builder()
                .name("Git")
                .command("git")
                .build();

        try {
            String version = executeCommand("git --version");
            if (version != null && !version.isEmpty()) {
                check.setInstalled(true);
                check.setVersion(version.replace("git version ", "").trim());
                check.setPath(executeCommand(isWindows() ? "where git" : "which git"));
            } else {
                check.setInstalled(false);
            }
        } catch (Exception e) {
            log.debug("Git not found: {}", e.getMessage());
            check.setInstalled(false);
        }

        return check;
    }

    /**
     * 执行命令并获取输出
     */
    private String executeCommand(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            if (isWindows()) {
                pb.command("cmd.exe", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }

            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);

            if (!finished) {
                process.destroy();
                return null;
            }

            if (process.exitValue() == 0) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    StringBuilder output = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                    return output.toString().trim();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to execute command '{}': {}", command, e.getMessage());
        }

        return null;
    }

    /**
     * 判断是否为 Windows 系统
     */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemInfo {
        private String os;
        private String osVersion;
        private String architecture;
        private String javaVersion;
        private String javaVendor;
        private String userHome;
        private String userName;
        private long totalMemory;
        private long freeMemory;
        private long maxMemory;
        private int availableProcessors;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnvironmentCheck {
        private String name;
        private String command;
        private boolean installed;
        private String version;
        private String path;
    }
}
