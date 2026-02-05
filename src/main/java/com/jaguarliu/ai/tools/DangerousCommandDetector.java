package com.jaguarliu.ai.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 危险命令检测器
 * 用于检测 shell 命令是否包含危险操作，需要 HITL 确认
 *
 * 检测的危险模式包括：
 * - 批量删除（rm -rf, del /s /q, rmdir /s）
 * - 密码/凭证相关（password, secret, credential, token）
 * - 系统级操作（格式化、权限修改等）
 * - 网络危险操作（curl | sh, wget | bash）
 */
@Slf4j
@Component
public class DangerousCommandDetector {

    /**
     * 危险命令模式（正则表达式）
     * 匹配到任意一个即认为是危险命令
     */
    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            // === 批量删除操作 ===
            // rm -rf / rm -r (递归删除)
            Pattern.compile("\\brm\\s+(-[a-zA-Z]*r[a-zA-Z]*|--recursive)", Pattern.CASE_INSENSITIVE),
            // Windows: del /s, del /q, rmdir /s
            Pattern.compile("\\b(del|erase)\\s+/[sq]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\brmdir\\s+/s", Pattern.CASE_INSENSITIVE),
            // rd /s (Windows)
            Pattern.compile("\\brd\\s+/s", Pattern.CASE_INSENSITIVE),

            // === 密码/凭证相关 ===
            // 命令中包含 password=xxx 或 --password
            Pattern.compile("(password|passwd|pwd)\\s*[=:]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("--password", Pattern.CASE_INSENSITIVE),
            // secret, credential, token 赋值
            Pattern.compile("(secret|credential|api[_-]?key|access[_-]?token)\\s*[=:]", Pattern.CASE_INSENSITIVE),

            // === 系统级危险操作 ===
            // 格式化磁盘
            Pattern.compile("\\bformat\\s+[a-zA-Z]:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bmkfs\\b", Pattern.CASE_INSENSITIVE),
            // 修改系统权限
            Pattern.compile("\\bchmod\\s+(-[a-zA-Z]*R|--recursive|777)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bchown\\s+(-[a-zA-Z]*R|--recursive)", Pattern.CASE_INSENSITIVE),
            // 危险的系统命令
            Pattern.compile("\\b(shutdown|reboot|init\\s+[06])\\b", Pattern.CASE_INSENSITIVE),

            // === 网络危险操作（远程代码执行） ===
            // curl xxx | sh/bash
            Pattern.compile("curl\\s+[^|]+\\|\\s*(sh|bash|zsh|python)", Pattern.CASE_INSENSITIVE),
            // wget xxx | sh/bash
            Pattern.compile("wget\\s+[^|]+\\|\\s*(sh|bash|zsh|python)", Pattern.CASE_INSENSITIVE),
            // curl -s xxx | sh
            Pattern.compile("curl\\s+-[a-zA-Z]*s[a-zA-Z]*.*\\|\\s*(sh|bash)", Pattern.CASE_INSENSITIVE),

            // === 数据库危险操作 ===
            // DROP DATABASE/TABLE
            Pattern.compile("\\bDROP\\s+(DATABASE|TABLE|SCHEMA)\\b", Pattern.CASE_INSENSITIVE),
            // TRUNCATE TABLE
            Pattern.compile("\\bTRUNCATE\\s+TABLE\\b", Pattern.CASE_INSENSITIVE),
            // DELETE without WHERE (危险的全表删除)
            Pattern.compile("\\bDELETE\\s+FROM\\s+\\w+\\s*;", Pattern.CASE_INSENSITIVE),

            // === Git 危险操作 ===
            // git push --force (可能覆盖远程历史)
            Pattern.compile("\\bgit\\s+push\\s+[^;]*--force", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bgit\\s+push\\s+-f\\b", Pattern.CASE_INSENSITIVE),
            // git reset --hard
            Pattern.compile("\\bgit\\s+reset\\s+--hard", Pattern.CASE_INSENSITIVE),
            // git clean -fd (删除未跟踪文件)
            Pattern.compile("\\bgit\\s+clean\\s+-[a-zA-Z]*f", Pattern.CASE_INSENSITIVE)
    );

    /**
     * 检测命令是否危险
     *
     * @param command shell 命令
     * @return true 如果命令包含危险模式，需要 HITL 确认
     */
    public boolean isDangerous(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }

        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(command).find()) {
                log.debug("Dangerous command detected: pattern={}, command={}", pattern.pattern(), command);
                return true;
            }
        }

        return false;
    }

    /**
     * 获取命令危险的原因（用于日志或提示）
     *
     * @param command shell 命令
     * @return 匹配的危险模式描述，如果不危险返回 null
     */
    public String getDangerReason(String command) {
        if (command == null || command.isBlank()) {
            return null;
        }

        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(command).find()) {
                return describePattern(pattern);
            }
        }

        return null;
    }

    /**
     * 根据模式返回人类可读的描述
     */
    private String describePattern(Pattern pattern) {
        String p = pattern.pattern();
        if (p.contains("rm") || p.contains("del") || p.contains("rmdir") || p.contains("rd")) {
            return "Recursive delete operation";
        }
        if (p.contains("password") || p.contains("secret") || p.contains("credential") || p.contains("token")) {
            return "Contains sensitive credentials";
        }
        if (p.contains("format") || p.contains("mkfs")) {
            return "Disk format operation";
        }
        if (p.contains("chmod") || p.contains("chown")) {
            return "Recursive permission change";
        }
        if (p.contains("shutdown") || p.contains("reboot")) {
            return "System shutdown/reboot";
        }
        if (p.contains("curl") || p.contains("wget")) {
            return "Remote code execution";
        }
        if (p.contains("DROP") || p.contains("TRUNCATE") || p.contains("DELETE")) {
            return "Database destructive operation";
        }
        if (p.contains("git")) {
            return "Dangerous git operation";
        }
        return "Potentially dangerous operation";
    }
}
