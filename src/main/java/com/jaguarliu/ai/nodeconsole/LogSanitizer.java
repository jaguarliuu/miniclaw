package com.jaguarliu.ai.nodeconsole;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 日志脱敏工具：防止命令、凭据、敏感信息泄露到日志
 */
@Component
public class LogSanitizer {

    /**
     * 生成命令摘要（仅记录长度和哈希，不记录完整内容）
     * 用于日志记录，避免泄露敏感命令参数（如 token、密码等）
     *
     * @param command 原始命令
     * @return 脱敏后的摘要字符串
     */
    public static String commandSummary(String command) {
        if (command == null || command.isEmpty()) {
            return "[empty]";
        }

        // 只记录长度和前8位哈希
        String hash = sha256Prefix(command, 8);
        return String.format("[cmd: len=%d, hash=%s]", command.length(), hash);
    }

    /**
     * 脱敏异常信息（仅记录异常类名，不记录 message）
     * 避免异常 message 中包含敏感信息（如连接字符串、内网地址等）
     *
     * @param throwable 异常对象
     * @return 脱敏后的异常描述
     */
    public static String sanitizeException(Throwable throwable) {
        if (throwable == null) {
            return "null";
        }
        return throwable.getClass().getSimpleName();
    }

    /**
     * 脱敏节点地址（用于对外响应）
     * 内网地址完全隐藏，公网地址保留前缀
     *
     * @param host 主机地址
     * @return 脱敏后的地址
     */
    public static String sanitizeHost(String host) {
        if (host == null || host.isEmpty()) {
            return "[unknown]";
        }

        // 内网地址完全隐藏
        if (host.startsWith("10.") || host.startsWith("172.") || host.startsWith("192.168.") ||
            host.equals("localhost") || host.equals("127.0.0.1")) {
            return "[internal]";
        }

        // 公网地址保留前缀
        if (host.length() > 10) {
            return host.substring(0, 10) + "...";
        }

        return host;
    }

    /**
     * SHA-256 哈希前缀（用于生成短摘要）
     */
    private static String sha256Prefix(String input, int prefixLength) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < Math.min(hash.length, (prefixLength + 1) / 2); i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.substring(0, Math.min(prefixLength, hexString.length()));
        } catch (NoSuchAlgorithmException e) {
            return "????????";
        }
    }
}
