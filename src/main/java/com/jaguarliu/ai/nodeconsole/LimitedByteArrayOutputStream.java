package com.jaguarliu.ai.nodeconsole;

import java.io.ByteArrayOutputStream;

/**
 * 限制最大字节数的 ByteArrayOutputStream，防止 OOM
 */
class LimitedByteArrayOutputStream extends ByteArrayOutputStream {
    private final int maxBytes;
    private long originalLength = 0;
    private boolean truncated = false;

    public LimitedByteArrayOutputStream(int maxBytes) {
        super(Math.min(maxBytes, 8192)); // 初始容量
        this.maxBytes = maxBytes;
    }

    @Override
    public synchronized void write(int b) {
        originalLength++;
        if (count < maxBytes) {
            super.write(b);
        } else {
            truncated = true;
        }
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) {
        originalLength += len;
        int remainingSpace = maxBytes - count;

        if (remainingSpace > 0) {
            int bytesToWrite = Math.min(len, remainingSpace);
            super.write(b, off, bytesToWrite);

            if (bytesToWrite < len) {
                truncated = true;
            }
        } else {
            truncated = true;
        }
    }

    public boolean isTruncated() {
        return truncated;
    }

    public long getOriginalLength() {
        return originalLength;
    }
}
