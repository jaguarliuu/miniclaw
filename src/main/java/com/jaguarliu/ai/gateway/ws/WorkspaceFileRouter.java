package com.jaguarliu.ai.gateway.ws;

import com.jaguarliu.ai.tools.ToolsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 工作空间文件下载端点
 * 提供 /api/workspace/** 路径下的文件下载服务
 * 仅允许访问工作空间目录内的文件，防止路径遍历
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WorkspaceFileRouter {

    private final ToolsProperties toolsProperties;

    @Bean
    public RouterFunction<ServerResponse> workspaceFileRoute() {
        return RouterFunctions.route()
                .GET("/api/workspace/**", request -> {
                    // 提取 /api/workspace/ 之后的路径
                    String fullPath = request.path();
                    String relativePath = fullPath.substring("/api/workspace/".length());

                    if (relativePath.isBlank()) {
                        return ServerResponse.badRequest().bodyValue("Missing file path");
                    }

                    // URL 解码
                    relativePath = URLDecoder.decode(relativePath, StandardCharsets.UTF_8);

                    // 解析并校验路径
                    Path workspaceRoot = Path.of(toolsProperties.getWorkspace()).toAbsolutePath().normalize();
                    Path filePath = workspaceRoot.resolve(relativePath).normalize();

                    // 路径遍历防护
                    if (!filePath.startsWith(workspaceRoot)) {
                        log.warn("Workspace download path traversal attempt: {}", relativePath);
                        return ServerResponse.status(403).bodyValue("Access denied");
                    }

                    // 文件存在性检查
                    if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                        return ServerResponse.notFound().build();
                    }

                    Resource resource = new FileSystemResource(filePath);
                    String fileName = filePath.getFileName().toString();
                    MediaType mediaType = guessMediaType(fileName);

                    // 判断是否应该作为附件下载（通过 query param）
                    boolean download = request.queryParam("download").isPresent();

                    var responseBuilder = ServerResponse.ok().contentType(mediaType);
                    if (download) {
                        responseBuilder = responseBuilder.header(
                                "Content-Disposition",
                                "attachment; filename=\"" + fileName + "\""
                        );
                    }

                    return responseBuilder.bodyValue(resource);
                })
                .build();
    }

    private static MediaType guessMediaType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return MediaType.TEXT_HTML;
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return MediaType.valueOf("application/javascript");
        if (lower.endsWith(".ts")) return MediaType.valueOf("text/plain");
        if (lower.endsWith(".css")) return MediaType.valueOf("text/css");
        if (lower.endsWith(".json")) return MediaType.APPLICATION_JSON;
        if (lower.endsWith(".xml")) return MediaType.APPLICATION_XML;
        if (lower.endsWith(".md")) return MediaType.valueOf("text/markdown");
        if (lower.endsWith(".txt")) return MediaType.TEXT_PLAIN;
        if (lower.endsWith(".csv")) return MediaType.valueOf("text/csv");
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".svg")) return MediaType.valueOf("image/svg+xml");
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (lower.endsWith(".zip")) return MediaType.valueOf("application/zip");
        if (lower.endsWith(".py")) return MediaType.valueOf("text/plain");
        if (lower.endsWith(".java")) return MediaType.valueOf("text/plain");
        if (lower.endsWith(".sql")) return MediaType.valueOf("text/plain");
        if (lower.endsWith(".sh")) return MediaType.valueOf("text/plain");
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return MediaType.valueOf("text/plain");
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
