package com.jaguarliu.ai.gateway.rpc.handler.skill;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.skills.parser.SkillParseResult;
import com.jaguarliu.ai.skills.parser.SkillParser;
import com.jaguarliu.ai.skills.registry.SkillRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.Base64;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * skills.upload - 上传 skill 文件（.md 或 .zip）
 *
 * 请求格式：
 * { "fileName": "xxx.md|xxx.zip", "content": "<base64>" }
 *
 * 返回格式：
 * { "name": "<skill-name>" }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillUploadHandler implements RpcHandler {

    private static final long MAX_CONTENT_SIZE = 1024 * 1024; // 1MB

    private final SkillRegistry skillRegistry;
    private final SkillParser skillParser;

    @Override
    public String getMethod() {
        return "skills.upload";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        Map<String, Object> payload = (Map<String, Object>) request.getPayload();

        if (payload == null) {
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing payload"));
        }

        String fileName = (String) payload.get("fileName");
        String content = (String) payload.get("content");

        if (fileName == null || fileName.isBlank()) {
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing required field: fileName"));
        }
        if (content == null || content.isBlank()) {
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing required field: content"));
        }

        String lowerName = fileName.toLowerCase();
        if (!lowerName.endsWith(".md") && !lowerName.endsWith(".zip")) {
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "File must be .md or .zip"));
        }

        // Decode base64
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(content);
        } catch (IllegalArgumentException e) {
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "Invalid base64 content"));
        }

        if (decoded.length > MAX_CONTENT_SIZE) {
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "File too large (max 1MB)"));
        }

        try {
            String skillName;
            if (lowerName.endsWith(".md")) {
                skillName = handleMdUpload(decoded);
            } else {
                skillName = handleZipUpload(decoded);
            }

            skillRegistry.refresh();

            log.info("Skill uploaded: {} from file {}", skillName, fileName);
            return Mono.just(RpcResponse.success(request.getId(), Map.of("name", skillName)));

        } catch (SkillUploadException e) {
            return Mono.just(RpcResponse.error(request.getId(), "UPLOAD_FAILED", e.getMessage()));
        } catch (IOException e) {
            log.error("Failed to upload skill: {}", fileName, e);
            return Mono.just(RpcResponse.error(request.getId(), "UPLOAD_FAILED", "Failed to write skill file"));
        }
    }

    private String handleMdUpload(byte[] content) throws IOException, SkillUploadException {
        Path userSkillsDir = skillRegistry.getUserSkillsDir();

        // Write to temp file for parsing
        Path tempFile = Files.createTempFile("skill-upload-", ".md");
        try {
            Files.write(tempFile, content);

            SkillParseResult result = skillParser.parse(tempFile, 1);
            if (!result.isValid()) {
                throw new SkillUploadException("Invalid skill file: " + result.getErrorMessage());
            }

            String name = result.getMetadata().getName();

            // Create skill directory and write SKILL.md
            Path skillDir = userSkillsDir.resolve(name);
            Files.createDirectories(skillDir);
            Files.write(skillDir.resolve("SKILL.md"), content);

            return name;
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private String handleZipUpload(byte[] content) throws IOException, SkillUploadException {
        Path userSkillsDir = skillRegistry.getUserSkillsDir();

        // Extract to temp directory
        Path tempDir = Files.createTempDirectory("skill-upload-");
        try {
            extractZip(content, tempDir);

            // Find SKILL.md in the extracted contents
            Path skillMd = findSkillMd(tempDir);
            if (skillMd == null) {
                throw new SkillUploadException("No SKILL.md found in zip archive");
            }

            SkillParseResult result = skillParser.parse(skillMd, 1);
            if (!result.isValid()) {
                throw new SkillUploadException("Invalid skill file: " + result.getErrorMessage());
            }

            String name = result.getMetadata().getName();

            // Determine source directory (the directory containing SKILL.md)
            Path sourceDir = skillMd.getParent();

            // Copy to user skills directory
            Path targetDir = userSkillsDir.resolve(name);
            if (Files.exists(targetDir)) {
                deleteRecursive(targetDir);
            }
            copyRecursive(sourceDir, targetDir);

            return name;
        } finally {
            deleteRecursive(tempDir);
        }
    }

    private void extractZip(byte[] content, Path targetDir) throws IOException, SkillUploadException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = targetDir.resolve(entry.getName()).normalize();

                // Zip-slip protection
                if (!entryPath.startsWith(targetDir)) {
                    throw new SkillUploadException("Invalid zip entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath);
                }

                zis.closeEntry();
            }
        }
    }

    private Path findSkillMd(Path dir) throws IOException {
        // Look for SKILL.md directly in the dir first
        Path direct = dir.resolve("SKILL.md");
        if (Files.exists(direct)) {
            return direct;
        }

        // Look one level deep (e.g., zip contains <name>/SKILL.md)
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(d -> d.resolve("SKILL.md"))
                    .filter(Files::exists)
                    .findFirst()
                    .orElse(null);
        }
    }

    private void copyRecursive(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (var stream = Files.walk(source)) {
            stream.forEach(src -> {
                try {
                    Path dest = target.resolve(source.relativize(src));
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private void deleteRecursive(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            // best effort cleanup
                        }
                    });
        } catch (IOException e) {
            // best effort cleanup
        }
    }

    private static class SkillUploadException extends Exception {
        SkillUploadException(String message) {
            super(message);
        }
    }
}
