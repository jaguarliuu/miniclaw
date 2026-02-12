package com.jaguarliu.ai.gateway.ws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * WebFlux 静态资源配置。
 * 当 miniclaw.webapp-dir 非空时启用，从指定目录提供前端静态文件，
 * 并为 SPA 路由提供 index.html fallback。
 *
 * 注意：必须使用 RequestPredicates 排除 /api/、/ws、/actuator 路径，
 * 否则 RouterFunctionMapping（order=-1）会先于 RequestMappingHandlerMapping（order=0）
 * 拦截这些路径导致 @RestController 端点无法被访问。
 */
@Configuration
@ConditionalOnExpression("!'${miniclaw.webapp-dir:}'.isEmpty()")
public class StaticResourceConfig {

    @Value("${miniclaw.webapp-dir}")
    private String webappDir;

    @Bean
    public RouterFunction<ServerResponse> staticResourceRouter() {
        Path root = Path.of(webappDir);

        // 排除后端路径：/api/**、/ws、/actuator/**
        // 让这些路径不被 RouterFunction 匹配，从而由 @RestController 处理
        var excludeBackend = RequestPredicates.path("/api/**").negate()
                .and(RequestPredicates.path("/ws").negate())
                .and(RequestPredicates.path("/actuator/**").negate());

        return RouterFunctions.route()
                .GET("/**", excludeBackend, request -> {
                    String path = request.path();

                    // 尝试作为静态文件提供
                    String filePath = path.equals("/") ? "index.html" : path.substring(1);
                    Path file = root.resolve(filePath).normalize();

                    // 安全检查：防止路径遍历
                    if (!file.startsWith(root)) {
                        return ServerResponse.notFound().build();
                    }

                    if (Files.exists(file) && Files.isRegularFile(file)) {
                        Resource resource = new FileSystemResource(file);
                        MediaType mediaType = guessMediaType(filePath);
                        return ServerResponse.ok()
                                .contentType(mediaType)
                                .bodyValue(resource);
                    }

                    // SPA fallback：路径不含扩展名 → 返回 index.html
                    if (!hasExtension(path)) {
                        Path indexFile = root.resolve("index.html");
                        if (Files.exists(indexFile)) {
                            Resource indexResource = new FileSystemResource(indexFile);
                            return ServerResponse.ok()
                                    .contentType(MediaType.TEXT_HTML)
                                    .bodyValue(indexResource);
                        }
                    }

                    return ServerResponse.notFound().build();
                })
                .build();
    }

    private static boolean hasExtension(String path) {
        String lastSegment = path.substring(path.lastIndexOf('/') + 1);
        return lastSegment.contains(".");
    }

    private static MediaType guessMediaType(String path) {
        if (path.endsWith(".html")) return MediaType.TEXT_HTML;
        if (path.endsWith(".js")) return MediaType.valueOf("application/javascript");
        if (path.endsWith(".css")) return MediaType.valueOf("text/css");
        if (path.endsWith(".json")) return MediaType.APPLICATION_JSON;
        if (path.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (path.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (path.endsWith(".svg")) return MediaType.valueOf("image/svg+xml");
        if (path.endsWith(".ico")) return MediaType.valueOf("image/x-icon");
        if (path.endsWith(".woff")) return MediaType.valueOf("font/woff");
        if (path.endsWith(".woff2")) return MediaType.valueOf("font/woff2");
        if (path.endsWith(".ttf")) return MediaType.valueOf("font/ttf");
        if (path.endsWith(".map")) return MediaType.APPLICATION_JSON;
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
