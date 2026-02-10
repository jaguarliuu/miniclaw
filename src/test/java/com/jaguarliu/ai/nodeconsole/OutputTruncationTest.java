package com.jaguarliu.ai.nodeconsole;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OutputTruncationTest {

    @Test
    void testLimitedOutputStreamTruncation() {
        int maxBytes = 100;
        LimitedByteArrayOutputStream los = new LimitedByteArrayOutputStream(maxBytes);

        byte[] data = new byte[200];
        for (int i = 0; i < 200; i++) {
            data[i] = (byte) ('A' + (i % 26));
        }

        los.write(data, 0, 200);

        assertTrue(los.isTruncated(), "Should be truncated");
        assertEquals(100, los.size(), "Should stop at max bytes");
        assertEquals(200, los.getOriginalLength(), "Should track original length");
    }

    @Test
    void testExecResultBuilder() {
        ExecResult result = new ExecResult.Builder()
            .stdout("output")
            .stderr("error")
            .exitCode(1)
            .truncated(true)
            .originalLength(50000)
            .build();

        assertTrue(result.isTruncated());
        assertEquals(50000, result.getOriginalLength());
        assertTrue(result.formatOutput().contains("truncated"));
    }
}
