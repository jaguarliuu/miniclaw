package com.jaguarliu.ai.memory.store;

import com.jaguarliu.ai.memory.MemoryProperties;
import com.jaguarliu.ai.tools.ToolsProperties;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MemoryStore 单元测试
 *
 * 测试覆盖：
 * 1. 初始化和目录创建
 * 2. appendToCore / appendToDaily 写入
 * 3. read / readLines 读取
 * 4. listFiles 列举
 * 5. 路径安全校验（防止目录穿越）
 * 6. 边界值和异常场景
 */
@DisplayName("MemoryStore Tests")
class MemoryStoreTest {

    @TempDir
    Path tempDir;

    private MemoryStore memoryStore;
    private MemoryProperties memoryProperties;
    private ToolsProperties toolsProperties;

    @BeforeEach
    void setUp() {
        memoryProperties = new MemoryProperties();
        memoryProperties.setPath("memory");

        toolsProperties = new ToolsProperties();
        toolsProperties.setWorkspace(tempDir.toString());

        memoryStore = new MemoryStore(memoryProperties, toolsProperties);
        memoryStore.init();
    }

    // ==================== 初始化测试 ====================

    @Nested
    @DisplayName("Initialization")
    class InitializationTests {

        @Test
        @DisplayName("初始化时创建 memory 目录")
        void initCreatesMemoryDirectory() {
            Path memoryDir = tempDir.resolve("memory");
            assertTrue(Files.exists(memoryDir));
            assertTrue(Files.isDirectory(memoryDir));
        }

        @Test
        @DisplayName("getMemoryDir 返回正确路径")
        void getMemoryDirReturnsCorrectPath() {
            Path expected = tempDir.resolve("memory").toAbsolutePath().normalize();
            assertEquals(expected, memoryStore.getMemoryDir());
        }

        @Test
        @DisplayName("初始化时核心记忆文件不存在")
        void coreMemoryNotExistsInitially() {
            assertFalse(memoryStore.coreMemoryExists());
        }
    }

    // ==================== appendToCore 测试 ====================

    @Nested
    @DisplayName("appendToCore")
    class AppendToCoreTests {

        @Test
        @DisplayName("首次写入创建 MEMORY.md")
        void firstAppendCreatesFile() throws IOException {
            memoryStore.appendToCore("Hello Memory");

            Path corePath = tempDir.resolve("memory/MEMORY.md");
            assertTrue(Files.exists(corePath));
            assertEquals("Hello Memory", Files.readString(corePath).trim());
        }

        @Test
        @DisplayName("多次追加用空行分隔")
        void multipleAppendsWithNewline() throws IOException {
            memoryStore.appendToCore("First");
            memoryStore.appendToCore("Second");

            Path corePath = tempDir.resolve("memory/MEMORY.md");
            String content = Files.readString(corePath);
            assertTrue(content.contains("First"));
            assertTrue(content.contains("Second"));
            assertTrue(content.contains("\n"));
        }

        @Test
        @DisplayName("写入后 coreMemoryExists 返回 true")
        void coreMemoryExistsAfterAppend() throws IOException {
            memoryStore.appendToCore("Test");
            assertTrue(memoryStore.coreMemoryExists());
        }

        @Test
        @DisplayName("边界值 - 写入空字符串")
        void appendEmptyString() throws IOException {
            memoryStore.appendToCore("");
            Path corePath = tempDir.resolve("memory/MEMORY.md");
            assertTrue(Files.exists(corePath));
        }

        @Test
        @DisplayName("边界值 - 写入包含特殊字符的内容")
        void appendSpecialCharacters() throws IOException {
            String content = "# 标题\n- 列表项\n```code```\n中文内容 🎉";
            memoryStore.appendToCore(content);

            Path corePath = tempDir.resolve("memory/MEMORY.md");
            String read = Files.readString(corePath);
            assertTrue(read.contains("标题"));
            assertTrue(read.contains("🎉"));
        }

        @Test
        @DisplayName("边界值 - 写入大量内容")
        void appendLargeContent() throws IOException {
            String largeContent = "x".repeat(100000);
            memoryStore.appendToCore(largeContent);

            Path corePath = tempDir.resolve("memory/MEMORY.md");
            assertEquals(100000, Files.readString(corePath).trim().length());
        }
    }

    // ==================== appendToDaily 测试 ====================

    @Nested
    @DisplayName("appendToDaily")
    class AppendToDailyTests {

        @Test
        @DisplayName("写入今日日记文件")
        void appendToDailyCreatesFile() throws IOException {
            memoryStore.appendToDaily("Today's note");

            String todayFileName = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".md";
            Path dailyPath = tempDir.resolve("memory/" + todayFileName);
            assertTrue(Files.exists(dailyPath));
            assertTrue(Files.readString(dailyPath).contains("Today's note"));
        }

        @Test
        @DisplayName("多次追加到同一天的日记")
        void multipleAppendsSameDay() throws IOException {
            memoryStore.appendToDaily("Morning");
            memoryStore.appendToDaily("Evening");

            String todayFileName = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".md";
            Path dailyPath = tempDir.resolve("memory/" + todayFileName);
            String content = Files.readString(dailyPath);
            assertTrue(content.contains("Morning"));
            assertTrue(content.contains("Evening"));
        }
    }

    // ==================== read 测试 ====================

    @Nested
    @DisplayName("read")
    class ReadTests {

        @Test
        @DisplayName("读取已存在的文件")
        void readExistingFile() throws IOException {
            memoryStore.appendToCore("Test content");
            String content = memoryStore.read("MEMORY.md");
            assertTrue(content.contains("Test content"));
        }

        @Test
        @DisplayName("读取不存在的文件抛出异常")
        void readNonExistentFile() {
            IOException exception = assertThrows(IOException.class, () -> {
                memoryStore.read("nonexistent.md");
            });
            assertTrue(exception.getMessage().contains("not found"));
        }

        @Test
        @DisplayName("读取日记文件")
        void readDailyFile() throws IOException {
            memoryStore.appendToDaily("Daily content");
            String todayFileName = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".md";
            String content = memoryStore.read(todayFileName);
            assertTrue(content.contains("Daily content"));
        }
    }

    // ==================== readLines 测试 ====================

    @Nested
    @DisplayName("readLines")
    class ReadLinesTests {

        @BeforeEach
        void createTestFile() throws IOException {
            Path testFile = tempDir.resolve("memory/test.md");
            Files.writeString(testFile, "Line1\nLine2\nLine3\nLine4\nLine5");
        }

        @Test
        @DisplayName("读取从第1行开始的3行")
        void readFromLine1() throws IOException {
            String content = memoryStore.readLines("test.md", 1, 3);
            assertEquals("Line1\nLine2\nLine3", content);
        }

        @Test
        @DisplayName("读取从第3行开始的2行")
        void readFromLine3() throws IOException {
            String content = memoryStore.readLines("test.md", 3, 2);
            assertEquals("Line3\nLine4", content);
        }

        @Test
        @DisplayName("边界值 - startLine 为 0（当作 1 处理）")
        void startLineZero() throws IOException {
            String content = memoryStore.readLines("test.md", 0, 2);
            assertEquals("Line1\nLine2", content);
        }

        @Test
        @DisplayName("边界值 - startLine 超出文件行数")
        void startLineBeyondFileLength() throws IOException {
            String content = memoryStore.readLines("test.md", 100, 10);
            assertEquals("", content);
        }

        @Test
        @DisplayName("边界值 - limit 超出剩余行数")
        void limitBeyondRemainingLines() throws IOException {
            String content = memoryStore.readLines("test.md", 4, 100);
            assertEquals("Line4\nLine5", content);
        }

        @Test
        @DisplayName("边界值 - limit 为 0")
        void limitZero() throws IOException {
            String content = memoryStore.readLines("test.md", 1, 0);
            assertEquals("", content);
        }

        @Test
        @DisplayName("读取不存在的文件抛出异常")
        void readLinesNonExistentFile() {
            assertThrows(IOException.class, () -> {
                memoryStore.readLines("nonexistent.md", 1, 10);
            });
        }
    }

    // ==================== listFiles 测试 ====================

    @Nested
    @DisplayName("listFiles")
    class ListFilesTests {

        @Test
        @DisplayName("空目录返回空列表")
        void emptyDirectoryReturnsEmptyList() throws IOException {
            List<MemoryStore.MemoryFileInfo> files = memoryStore.listFiles();
            assertTrue(files.isEmpty());
        }

        @Test
        @DisplayName("列出所有 .md 文件")
        void listAllMdFiles() throws IOException {
            memoryStore.appendToCore("Core content");
            memoryStore.appendToDaily("Daily content");

            List<MemoryStore.MemoryFileInfo> files = memoryStore.listFiles();
            assertEquals(2, files.size());
        }

        @Test
        @DisplayName("不列出非 .md 文件")
        void excludeNonMdFiles() throws IOException {
            memoryStore.appendToCore("Content");
            // 创建一个非 .md 文件
            Files.writeString(tempDir.resolve("memory/test.txt"), "text file");

            List<MemoryStore.MemoryFileInfo> files = memoryStore.listFiles();
            assertEquals(1, files.size());
            assertEquals("MEMORY.md", files.get(0).relativePath());
        }

        @Test
        @DisplayName("文件信息包含正确的大小")
        void fileInfoContainsCorrectSize() throws IOException {
            memoryStore.appendToCore("12345");

            List<MemoryStore.MemoryFileInfo> files = memoryStore.listFiles();
            assertTrue(files.get(0).sizeBytes() >= 5);
        }

        @Test
        @DisplayName("文件按路径降序排列")
        void filesSortedByPathDescending() throws IOException {
            Files.writeString(tempDir.resolve("memory/aaa.md"), "a");
            Files.writeString(tempDir.resolve("memory/zzz.md"), "z");

            List<MemoryStore.MemoryFileInfo> files = memoryStore.listFiles();
            assertEquals("zzz.md", files.get(0).relativePath());
            assertEquals("aaa.md", files.get(1).relativePath());
        }
    }

    // ==================== 路径安全测试 ====================

    @Nested
    @DisplayName("Path Security")
    class PathSecurityTests {

        @Test
        @DisplayName("阻止目录穿越攻击 - 读取")
        void preventDirectoryTraversalRead() {
            IOException exception = assertThrows(IOException.class, () -> {
                memoryStore.read("../../../etc/passwd");
            });
            assertTrue(exception.getMessage().contains("Access denied") ||
                       exception.getMessage().contains("not found"));
        }

        @Test
        @DisplayName("阻止目录穿越攻击 - readLines")
        void preventDirectoryTraversalReadLines() {
            assertThrows(IOException.class, () -> {
                memoryStore.readLines("../../secret.md", 1, 10);
            });
        }

        @Test
        @DisplayName("阻止绝对路径访问")
        void preventAbsolutePathAccess() {
            assertThrows(IOException.class, () -> {
                memoryStore.read("/etc/passwd");
            });
        }
    }

    // ==================== MemoryFileInfo record 测试 ====================

    @Nested
    @DisplayName("MemoryFileInfo")
    class MemoryFileInfoTests {

        @Test
        @DisplayName("record 字段访问正常")
        void recordFieldsAccessible() {
            MemoryStore.MemoryFileInfo info = new MemoryStore.MemoryFileInfo("test.md", 100, 1234567890L);
            assertEquals("test.md", info.relativePath());
            assertEquals(100, info.sizeBytes());
            assertEquals(1234567890L, info.lastModifiedMs());
        }

        @Test
        @DisplayName("相同值的 record 相等")
        void equalRecords() {
            MemoryStore.MemoryFileInfo info1 = new MemoryStore.MemoryFileInfo("test.md", 100, 1234567890L);
            MemoryStore.MemoryFileInfo info2 = new MemoryStore.MemoryFileInfo("test.md", 100, 1234567890L);
            assertEquals(info1, info2);
        }

        @Test
        @DisplayName("边界值 - sizeBytes 为 0")
        void sizeBytesZero() {
            MemoryStore.MemoryFileInfo info = new MemoryStore.MemoryFileInfo("empty.md", 0, 0L);
            assertEquals(0, info.sizeBytes());
        }
    }
}
