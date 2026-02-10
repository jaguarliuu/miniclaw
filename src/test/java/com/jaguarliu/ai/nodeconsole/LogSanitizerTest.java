package com.jaguarliu.ai.nodeconsole;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LogSanitizerTest {

    @Test
    void testCommandSummaryHidesContent() {
        String sensitiveCommand = "curl -H 'Authorization: Bearer secret-token-12345' https://api.example.com";
        String summary = LogSanitizer.commandSummary(sensitiveCommand);

        // 不应包含敏感内容
        assertFalse(summary.contains("secret-token"), "Should not contain token");
        assertFalse(summary.contains("Bearer"), "Should not contain auth header");
        assertFalse(summary.contains("api.example.com"), "Should not contain URL");

        // 应包含长度和哈希
        assertTrue(summary.contains("len="), "Should contain length");
        assertTrue(summary.contains("hash="), "Should contain hash");
        assertTrue(summary.contains(String.valueOf(sensitiveCommand.length())), "Should show actual length");
    }

    @Test
    void testCommandSummaryEmptyString() {
        String summary = LogSanitizer.commandSummary("");
        assertEquals("[empty]", summary);
    }

    @Test
    void testCommandSummaryNull() {
        String summary = LogSanitizer.commandSummary(null);
        assertEquals("[empty]", summary);
    }

    @Test
    void testSanitizeExceptionOnlyShowsClassName() {
        Exception ex = new IllegalArgumentException("Sensitive error: password=abc123");
        String sanitized = LogSanitizer.sanitizeException(ex);

        assertEquals("IllegalArgumentException", sanitized);
        assertFalse(sanitized.contains("password"), "Should not contain exception message");
        assertFalse(sanitized.contains("abc123"), "Should not contain sensitive data");
    }

    @Test
    void testSanitizeHostInternalAddresses() {
        assertEquals("[internal]", LogSanitizer.sanitizeHost("10.0.0.1"));
        assertEquals("[internal]", LogSanitizer.sanitizeHost("192.168.1.100"));
        assertEquals("[internal]", LogSanitizer.sanitizeHost("172.16.0.1"));
        assertEquals("[internal]", LogSanitizer.sanitizeHost("localhost"));
        assertEquals("[internal]", LogSanitizer.sanitizeHost("127.0.0.1"));
    }

    @Test
    void testSanitizeHostPublicAddresses() {
        String result = LogSanitizer.sanitizeHost("203.0.113.42");
        assertTrue(result.startsWith("203.0.113."), "Should preserve prefix");
        assertTrue(result.endsWith("..."), "Should truncate with ...");

        // 短地址不截断
        String shortHost = LogSanitizer.sanitizeHost("8.8.8.8");
        assertEquals("8.8.8.8", shortHost, "Short addresses not truncated");
    }

    @Test
    void testSanitizeHostNull() {
        assertEquals("[unknown]", LogSanitizer.sanitizeHost(null));
        assertEquals("[unknown]", LogSanitizer.sanitizeHost(""));
    }
}
