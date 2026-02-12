package com.jaguarliu.ai.gateway.rpc.handler.system;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 文件/目录浏览处理器
 * 用于数据源配置时选择文件或目录
 */
@Slf4j
@Component
public class FileBrowseHandler implements RpcHandler {

    @Value("${files.browse-roots:}")
    private String configBrowseRoots;

    @Override
    public String getMethod() {
        return "files.browse";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        return Mono.fromCallable(() -> {
            Map<?, ?> payload = (request.getPayload() instanceof Map) ? (Map<?, ?>) request.getPayload() : Map.of();
            String pathStr = payload.get("path") != null ? payload.get("path").toString() : null;
            String mode = payload.get("mode") != null ? payload.get("mode").toString() : "all"; // all / dir / file

            Path browsePath;
            if (pathStr == null || pathStr.isBlank()) {
                // 默认返回可浏览的根目录列表
                return RpcResponse.success(request.getId(), Map.of(
                        "path", "",
                        "entries", getRootEntries()
                ));
            }

            browsePath = Path.of(pathStr).toAbsolutePath().normalize();

            if (!Files.exists(browsePath)) {
                return RpcResponse.error(request.getId(), "NOT_FOUND", "Path does not exist: " + pathStr);
            }

            if (!Files.isDirectory(browsePath)) {
                return RpcResponse.error(request.getId(), "NOT_DIR", "Path is not a directory: " + pathStr);
            }

            List<Map<String, Object>> entries = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(browsePath)) {
                for (Path entry : stream) {
                    // 跳过隐藏文件
                    if (entry.getFileName().toString().startsWith(".")) {
                        continue;
                    }

                    boolean isDir = Files.isDirectory(entry);

                    // 根据 mode 过滤
                    if ("dir".equals(mode) && !isDir) continue;
                    if ("file".equals(mode) && isDir) continue;

                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", entry.getFileName().toString());
                    item.put("path", entry.toAbsolutePath().normalize().toString());
                    item.put("isDirectory", isDir);
                    if (!isDir) {
                        try {
                            item.put("size", Files.size(entry));
                        } catch (IOException e) {
                            item.put("size", 0);
                        }
                    }
                    entries.add(item);
                }
            }

            // 排序：目录在前，文件在后，各自按名称排序
            entries.sort(Comparator
                    .<Map<String, Object>, Boolean>comparing(e -> !(Boolean) e.get("isDirectory"))
                    .thenComparing(e -> ((String) e.get("name")).toLowerCase()));

            // 父目录
            String parent = browsePath.getParent() != null
                    ? browsePath.getParent().toAbsolutePath().normalize().toString()
                    : null;

            return RpcResponse.success(request.getId(), Map.of(
                    "path", browsePath.toString(),
                    "parent", parent != null ? parent : "",
                    "entries", entries
            ));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取根目录列表：系统盘根 + 用户目录 + 配置的额外根目录
     */
    private List<Map<String, Object>> getRootEntries() {
        List<Map<String, Object>> roots = new ArrayList<>();

        // 系统文件根目录
        for (Path root : Path.of("").getFileSystem().getRootDirectories()) {
            roots.add(Map.of(
                    "name", root.toString(),
                    "path", root.toAbsolutePath().normalize().toString(),
                    "isDirectory", true
            ));
        }

        // 用户主目录
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            roots.add(Map.of(
                    "name", "Home (" + userHome + ")",
                    "path", Path.of(userHome).toAbsolutePath().normalize().toString(),
                    "isDirectory", true
            ));
        }

        // 配置的额外根目录
        if (configBrowseRoots != null && !configBrowseRoots.isBlank()) {
            for (String extra : configBrowseRoots.split(",")) {
                String trimmed = extra.trim();
                if (!trimmed.isEmpty() && Files.isDirectory(Path.of(trimmed))) {
                    roots.add(Map.of(
                            "name", trimmed,
                            "path", Path.of(trimmed).toAbsolutePath().normalize().toString(),
                            "isDirectory", true
                    ));
                }
            }
        }

        return roots;
    }
}
