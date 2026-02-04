package com.jaguarliu.ai.skills.gating;

import com.jaguarliu.ai.skills.model.SkillRequires;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Skill 可用性检查服务
 *
 * 评估 SkillRequires 中的各项条件：
 * - env: 环境变量是否存在
 * - bins: 必需二进制程序是否在 PATH 中
 * - anyBins: 任一二进制程序存在即可
 * - config: 配置项是否为 true
 * - os: 当前操作系统是否在支持列表中
 */
@Slf4j
@Service
public class SkillGatingService {

    private final Environment springEnv;
    private final String currentOs;

    // 二进制检查缓存（避免重复执行 which/where 命令）
    private final Map<String, Boolean> binExistsCache = new ConcurrentHashMap<>();

    // Windows 判断
    private final boolean isWindows;

    public SkillGatingService(Environment springEnv) {
        this.springEnv = springEnv;
        this.currentOs = detectOs();
        this.isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * 评估 skill 的可用性条件
     *
     * @param requires 可用性条件，可为 null
     * @return 评估结果
     */
    public GatingResult evaluate(SkillRequires requires) {
        // 无条件时直接通过
        if (requires == null) {
            return GatingResult.PASSED;
        }

        List<String> missingEnvVars = checkEnvVars(requires.getEnv());
        List<String> missingBins = checkBins(requires.getBins());
        List<String> unsatisfiedAnyBins = checkAnyBins(requires.getAnyBins());
        List<String> missingConfigs = checkConfigs(requires.getConfig());
        String unsupportedOs = checkOs(requires.getOs());

        boolean available = missingEnvVars.isEmpty()
                && missingBins.isEmpty()
                && unsatisfiedAnyBins.isEmpty()
                && missingConfigs.isEmpty()
                && unsupportedOs == null;

        return GatingResult.builder()
                .available(available)
                .missingEnvVars(missingEnvVars)
                .missingBins(missingBins)
                .unsatisfiedAnyBins(unsatisfiedAnyBins)
                .missingConfigs(missingConfigs)
                .unsupportedOs(unsupportedOs)
                .build();
    }

    /**
     * 检查环境变量
     */
    private List<String> checkEnvVars(List<String> envVars) {
        if (envVars == null || envVars.isEmpty()) {
            return List.of();
        }

        List<String> missing = new ArrayList<>();
        for (String envVar : envVars) {
            String value = System.getenv(envVar);
            if (value == null || value.isBlank()) {
                missing.add(envVar);
            }
        }
        return missing;
    }

    /**
     * 检查必需二进制程序（全部必须存在）
     */
    private List<String> checkBins(List<String> bins) {
        if (bins == null || bins.isEmpty()) {
            return List.of();
        }

        List<String> missing = new ArrayList<>();
        for (String bin : bins) {
            if (!isBinaryExists(bin)) {
                missing.add(bin);
            }
        }
        return missing;
    }

    /**
     * 检查可选二进制程序（任一存在即可）
     */
    private List<String> checkAnyBins(List<String> anyBins) {
        if (anyBins == null || anyBins.isEmpty()) {
            return List.of();
        }

        for (String bin : anyBins) {
            if (isBinaryExists(bin)) {
                return List.of(); // 找到一个就满足
            }
        }

        // 全部不存在，返回候选列表
        return new ArrayList<>(anyBins);
    }

    /**
     * 检查配置项
     */
    private List<String> checkConfigs(List<String> configs) {
        if (configs == null || configs.isEmpty()) {
            return List.of();
        }

        List<String> missing = new ArrayList<>();
        for (String configKey : configs) {
            // 从 Spring Environment 读取配置
            String value = springEnv.getProperty(configKey);
            if (value == null || !Boolean.parseBoolean(value)) {
                missing.add(configKey);
            }
        }
        return missing;
    }

    /**
     * 检查操作系统
     */
    private String checkOs(List<String> supportedOs) {
        if (supportedOs == null || supportedOs.isEmpty()) {
            return null; // 无限制
        }

        if (supportedOs.contains(currentOs)) {
            return null; // 当前 OS 在支持列表中
        }

        return currentOs; // 返回不支持的当前 OS
    }

    /**
     * 检测当前操作系统
     * 返回标准化名称：darwin / linux / win32
     */
    private String detectOs() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("mac") || osName.contains("darwin")) {
            return "darwin";
        } else if (osName.contains("linux")) {
            return "linux";
        } else if (osName.contains("win")) {
            return "win32";
        }
        return osName; // 未知系统返回原始名称
    }

    /**
     * 检查二进制程序是否存在于 PATH 中
     * 使用缓存避免重复执行命令
     */
    private boolean isBinaryExists(String binName) {
        return binExistsCache.computeIfAbsent(binName, this::checkBinaryInPath);
    }

    /**
     * 实际执行命令检查二进制是否存在
     */
    private boolean checkBinaryInPath(String binName) {
        try {
            ProcessBuilder pb;
            if (isWindows) {
                // Windows: 使用 where 命令
                pb = new ProcessBuilder("where", binName);
            } else {
                // Unix: 使用 which 命令
                pb = new ProcessBuilder("which", binName);
            }

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 设置超时，避免阻塞
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Binary check timed out for: {}", binName);
                return false;
            }

            int exitCode = process.exitValue();
            return exitCode == 0;

        } catch (Exception e) {
            log.warn("Failed to check binary existence: {}", binName, e);
            return false;
        }
    }

    /**
     * 清除二进制检查缓存（用于测试或配置变更）
     */
    public void clearBinCache() {
        binExistsCache.clear();
    }

    /**
     * 获取当前检测到的操作系统
     */
    public String getCurrentOs() {
        return currentOs;
    }
}
