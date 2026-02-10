package com.jaguarliu.ai.nodeconsole;

import com.jaguarliu.ai.tools.DangerousCommandDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 远程命令安全分类器（基于 DangerousCommandDetector）
 *
 * Level 0 (READ_ONLY)：安全的只读命令，可自动执行
 * Level 1 (SIDE_EFFECT)：有副作用的命令，需要 HITL 确认
 * Level 2 (DESTRUCTIVE)：破坏性命令，永远禁止
 *
 * 复用系统现有的 DangerousCommandDetector 进行危险命令检测，
 * 额外添加只读命令白名单识别
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RemoteCommandClassifier {

    private final DangerousCommandDetector dangerousCommandDetector;

    /**
     * 安全等级
     */
    public enum SafetyLevel {
        READ_ONLY,     // Level 0
        SIDE_EFFECT,   // Level 1
        DESTRUCTIVE    // Level 2
    }

    public record Classification(SafetyLevel safetyLevel, String reason, String policy) {
        /**
         * 获取数字级别 (0/1/2)
         */
        public int level() {
            return switch (safetyLevel) {
                case READ_ONLY -> 0;
                case SIDE_EFFECT -> 1;
                case DESTRUCTIVE -> 2;
            };
        }

        /**
         * 根据策略判断是否需要 HITL
         */
        public boolean requiresHitl() {
            // Level 2 永远拒绝（不是 HITL，是直接 BLOCK）
            if (safetyLevel == SafetyLevel.DESTRUCTIVE) {
                return false;
            }

            // Level 1 (SIDE_EFFECT): relaxed 自动执行，否则需要 HITL
            if (safetyLevel == SafetyLevel.SIDE_EFFECT) {
                return !"relaxed".equals(policy);
            }

            // Level 0 (READ_ONLY): strict 需要 HITL，否则自动执行
            return "strict".equals(policy);
        }

        /**
         * 是否被策略阻止
         */
        public boolean isBlocked() {
            return safetyLevel == SafetyLevel.DESTRUCTIVE; // DESTRUCTIVE 永远阻止
        }
    }

    // ==================== Level 0: READ_ONLY (安全只读命令白名单) ====================

    private static final List<Pattern> READ_ONLY_PATTERNS = List.of(
            // 系统信息
            Pattern.compile("^\\s*(df|free|top|ps|uptime|uname|hostname|whoami|id|w|last|who)\\b"),
            Pattern.compile("^\\s*(cat|ls|grep|tail|head|less|more|wc|find|file|stat)\\b"),
            Pattern.compile("^\\s*(systemctl\\s+status|journalctl|dmesg)\\b"),
            Pattern.compile("^\\s*(mount|lsblk|lscpu|lsmem|lspci|lsusb)\\b"),
            Pattern.compile("^\\s*(ip\\s+(addr|route|link)|ifconfig|netstat|ss)\\b"),

            // Kubernetes 只读
            Pattern.compile("^\\s*kubectl\\s+(get|describe|logs|top)\\b"),
            Pattern.compile("^\\s*kubectl\\s+cluster-info\\b"),
            Pattern.compile("^\\s*kubectl\\s+version\\b"),

            // Docker 只读
            Pattern.compile("^\\s*(docker|podman)\\s+(ps|images|logs|inspect|stats|info|version)\\b"),

            // 网络诊断
            Pattern.compile("^\\s*(ping|traceroute|tracepath|dig|nslookup|host|curl\\s+-s?I)\\b"),

            // 数据库只读
            Pattern.compile("^\\s*SELECT\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*SHOW\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*DESCRIBE\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*EXPLAIN\\b", Pattern.CASE_INSENSITIVE)
    );

    /**
     * 分类命令（使用系统统一的 DangerousCommandDetector）
     *
     * @param command 命令内容
     * @param policy  节点安全策略 (strict / standard / relaxed)
     * @return 分类结果
     */
    public Classification classify(String command, String policy) {
        if (command == null || command.isBlank()) {
            return new Classification(SafetyLevel.SIDE_EFFECT, "Empty command", policy);
        }

        // Step 1: 使用系统的 DangerousCommandDetector 检测危险命令
        // 这些是 Level 2 (DESTRUCTIVE) - 永远禁止
        if (dangerousCommandDetector.isDangerous(command)) {
            String reason = dangerousCommandDetector.getDangerReason(command);
            return new Classification(SafetyLevel.DESTRUCTIVE,
                    "Dangerous command detected: " + reason, policy);
        }

        // Step 2: 检查 Level 0 (READ_ONLY) 白名单
        boolean isReadOnly = false;
        for (Pattern pattern : READ_ONLY_PATTERNS) {
            if (pattern.matcher(command).find()) {
                isReadOnly = true;
                break;
            }
        }

        // Step 3: 根据策略调整
        if (isReadOnly) {
            if ("strict".equals(policy)) {
                // strict: Level 0 提升为 Level 1（所有命令都需确认）
                return new Classification(SafetyLevel.SIDE_EFFECT,
                        "Read-only command, but strict policy requires confirmation", policy);
            }
            return new Classification(SafetyLevel.READ_ONLY, "Read-only command", policy);
        }

        // 默认 Level 1 (SIDE_EFFECT) - 不在白名单中的命令
        if ("relaxed".equals(policy)) {
            // relaxed: Level 1 降为 Level 0（副作用命令自动执行）
            return new Classification(SafetyLevel.READ_ONLY,
                    "Side-effect command, but relaxed policy allows auto-execution", policy);
        }

        return new Classification(SafetyLevel.SIDE_EFFECT, "Command with potential side effects", policy);
    }
}
