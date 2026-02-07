package com.jaguarliu.ai.runtime;

/**
 * Incremental extractor for write_file tool call JSON arguments.
 * <p>
 * As LLM streams tool call argument fragments (partial JSON), this class
 * accumulates the raw text and incrementally extracts:
 * <ul>
 *   <li>{@code path} (or {@code filePath}) - detected once</li>
 *   <li>{@code content} - streamed incrementally with proper JSON un-escaping</li>
 * </ul>
 */
public class ArtifactStreamExtractor {

    private boolean active;
    private final StringBuilder buffer = new StringBuilder();
    private String path;
    private boolean contentFieldFound;
    private int contentValueStart = -1; // index right after the opening quote of the content value
    private int scanPos;               // how far we've scanned for un-escaped content
    private boolean escapePending;     // previous fragment ended with a backslash
    private final StringBuilder pendingContent = new StringBuilder(); // un-escaped content since last flush

    public record ExtractionResult(
            String pathDetected,   // non-null when path is first detected
            String contentDelta    // non-null when new content is available
    ) {}

    public void activate() {
        this.active = true;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Append a raw JSON argument fragment and return any newly extracted data.
     */
    public ExtractionResult append(String fragment) {
        if (!active || fragment == null) {
            return new ExtractionResult(null, null);
        }

        buffer.append(fragment);
        String pathResult = null;
        String contentResult = null;

        // 1. Try to extract path (only once)
        if (path == null) {
            path = extractStringField("path");
            if (path == null) {
                path = extractStringField("filePath");
            }
            if (path != null) {
                pathResult = path;
            }
        }

        // 2. Locate content field start (only once)
        if (path != null && !contentFieldFound) {
            locateContentField();
        }

        // 3. Incrementally un-escape content
        if (contentFieldFound) {
            String delta = parseNewContent();
            if (delta != null && !delta.isEmpty()) {
                contentResult = delta;
            }
        }

        return new ExtractionResult(pathResult, contentResult);
    }

    /**
     * Extract a JSON string field value by key from the buffer.
     * Simple approach: find "key" followed by : and "value".
     */
    private String extractStringField(String key) {
        String searchKey = "\"" + key + "\"";
        int keyIdx = buffer.indexOf(searchKey);
        if (keyIdx < 0) return null;

        // Find the colon after the key
        int colonIdx = -1;
        for (int i = keyIdx + searchKey.length(); i < buffer.length(); i++) {
            char c = buffer.charAt(i);
            if (c == ':') {
                colonIdx = i;
                break;
            } else if (!Character.isWhitespace(c)) {
                return null; // unexpected char
            }
        }
        if (colonIdx < 0) return null;

        // Find opening quote of value
        int openQuote = -1;
        for (int i = colonIdx + 1; i < buffer.length(); i++) {
            char c = buffer.charAt(i);
            if (c == '"') {
                openQuote = i;
                break;
            } else if (!Character.isWhitespace(c)) {
                return null; // not a string value
            }
        }
        if (openQuote < 0) return null;

        // Find closing quote (respecting escapes)
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = openQuote + 1; i < buffer.length(); i++) {
            char c = buffer.charAt(i);
            if (escaped) {
                switch (c) {
                    case '"' -> value.append('"');
                    case '\\' -> value.append('\\');
                    case '/' -> value.append('/');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'u' -> {
                        if (i + 4 < buffer.length()) {
                            String hex = buffer.substring(i + 1, i + 5);
                            try {
                                value.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (NumberFormatException e) {
                                value.append('\\').append('u');
                            }
                        } else {
                            return null; // incomplete unicode escape
                        }
                    }
                    default -> value.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                // For path/filePath, this is the complete field — return it
                // But we must not match the "content" field here
                if (!key.equals("content")) {
                    return value.toString();
                }
            } else {
                value.append(c);
            }
        }
        return null; // string not yet closed
    }

    /**
     * Locate the start of the "content" field's string value in the buffer.
     */
    private void locateContentField() {
        String searchKey = "\"content\"";
        int keyIdx = buffer.indexOf(searchKey);
        if (keyIdx < 0) return;

        // Find colon
        int colonIdx = -1;
        for (int i = keyIdx + searchKey.length(); i < buffer.length(); i++) {
            char c = buffer.charAt(i);
            if (c == ':') {
                colonIdx = i;
                break;
            } else if (!Character.isWhitespace(c)) {
                return;
            }
        }
        if (colonIdx < 0) return;

        // Find opening quote
        for (int i = colonIdx + 1; i < buffer.length(); i++) {
            char c = buffer.charAt(i);
            if (c == '"') {
                contentFieldFound = true;
                contentValueStart = i + 1;
                scanPos = contentValueStart;
                return;
            } else if (!Character.isWhitespace(c)) {
                return;
            }
        }
    }

    /**
     * Parse newly arrived content characters with JSON un-escaping.
     * Returns the new un-escaped content delta, or null if nothing new.
     */
    private String parseNewContent() {
        if (contentValueStart < 0 || scanPos >= buffer.length()) {
            return null;
        }

        pendingContent.setLength(0);

        while (scanPos < buffer.length()) {
            char c = buffer.charAt(scanPos);

            if (escapePending) {
                escapePending = false;
                switch (c) {
                    case '"' -> pendingContent.append('"');
                    case '\\' -> pendingContent.append('\\');
                    case '/' -> pendingContent.append('/');
                    case 'n' -> pendingContent.append('\n');
                    case 'r' -> pendingContent.append('\r');
                    case 't' -> pendingContent.append('\t');
                    case 'b' -> pendingContent.append('\b');
                    case 'f' -> pendingContent.append('\f');
                    case 'u' -> {
                        if (scanPos + 4 < buffer.length()) {
                            String hex = buffer.substring(scanPos + 1, scanPos + 5);
                            try {
                                pendingContent.append((char) Integer.parseInt(hex, 16));
                                scanPos += 4;
                            } catch (NumberFormatException e) {
                                pendingContent.append('\\').append('u');
                            }
                        } else {
                            // Not enough chars for unicode escape, wait for more data
                            escapePending = true; // re-enter escape state
                            // Back up: put \\u back by not advancing
                            scanPos--; // will be re-incremented below but we break
                            break;
                        }
                    }
                    default -> pendingContent.append(c);
                }
                scanPos++;
                continue;
            }

            if (c == '\\') {
                if (scanPos == buffer.length() - 1) {
                    // Backslash at end of current buffer — wait for next fragment
                    escapePending = true;
                    scanPos++;
                    break;
                }
                escapePending = true;
                scanPos++;
                continue;
            }

            if (c == '"') {
                // End of content string — don't advance past it
                // Content is complete
                scanPos++;
                break;
            }

            pendingContent.append(c);
            scanPos++;
        }

        return pendingContent.length() > 0 ? pendingContent.toString() : null;
    }
}
