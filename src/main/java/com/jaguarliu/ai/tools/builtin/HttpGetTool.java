package com.jaguarliu.ai.tools.builtin;

import com.jaguarliu.ai.tools.Tool;
import com.jaguarliu.ai.tools.ToolConfigProperties;
import com.jaguarliu.ai.tools.ToolDefinition;
import com.jaguarliu.ai.tools.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * HTTP GET 工具
 */
@Slf4j
@Component
public class HttpGetTool implements Tool {

    private final WebClient webClient;
    private final ToolConfigProperties toolConfigProperties;

    /**
     * 最大响应体长度（截断保护）
     */
    private static final int MAX_RESPONSE_LENGTH = 32000;

    public HttpGetTool(ToolConfigProperties toolConfigProperties) {
        this.toolConfigProperties = toolConfigProperties;
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024)) // 2MB
                .build();
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("http_get")
                .description("发送 HTTP GET 请求并返回响应体。适用于获取网页内容或 API 数据。")
                .parameters(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "url", Map.of(
                                        "type", "string",
                                        "description", "请求的 URL"
                                )
                        ),
                        "required", List.of("url")
                ))
                .hitl(false)
                .build();
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> arguments) {
        String url = (String) arguments.get("url");
        if (url == null || url.isBlank()) {
            return Mono.just(ToolResult.error("Missing required parameter: url"));
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return Mono.just(ToolResult.error("Invalid URL: must start with http:// or https://"));
        }

        // 域名可信检查
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || !toolConfigProperties.isDomainTrusted(host)) {
                return Mono.just(ToolResult.error(
                        "Domain '" + host + "' is not in the trusted list. "
                                + "Add it via Settings > Tools to allow access."));
            }
        } catch (Exception e) {
            return Mono.just(ToolResult.error("Invalid URL: " + url));
        }

        log.info("HTTP GET: {}", url);

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .map(body -> {
                    if (body.length() > MAX_RESPONSE_LENGTH) {
                        body = body.substring(0, MAX_RESPONSE_LENGTH) + "\n\n[Truncated: response exceeds " + MAX_RESPONSE_LENGTH + " chars]";
                    }
                    log.info("HTTP GET completed: {} ({} chars)", url, body.length());
                    return ToolResult.success(body);
                })
                .onErrorResume(e -> {
                    log.error("HTTP GET failed: {}", url, e);
                    return Mono.just(ToolResult.error("HTTP request failed: " + e.getMessage()));
                });
    }
}
