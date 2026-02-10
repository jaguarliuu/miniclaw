package com.jaguarliu.ai.nodeconsole;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Connector 工厂
 * 自动发现并注册所有 Connector bean
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectorFactory {

    private final List<Connector> connectors;
    private final Map<String, Connector> registry = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        for (Connector connector : connectors) {
            String type = connector.getType();
            Connector existing = registry.putIfAbsent(type, connector);

            if (existing != null) {
                // 检测到重复类型，启动失败
                throw new IllegalStateException(
                    String.format("Duplicate connector type '%s': %s and %s. " +
                                  "Each connector must have a unique type identifier.",
                        type,
                        existing.getClass().getName(),
                        connector.getClass().getName())
                );
            }

            log.info("Registered connector: {} ({})", type, connector.getClass().getSimpleName());
        }
    }

    /**
     * 获取指定类型的连接器
     *
     * @param type 连接器类型（ssh / k8s / db）
     * @return 连接器实例
     * @throws IllegalArgumentException 未知类型
     */
    public Connector get(String type) {
        Connector connector = registry.get(type);
        if (connector == null) {
            throw new IllegalArgumentException("Unknown connector type: " + type);
        }
        return connector;
    }

    /**
     * 是否支持该连接器类型
     */
    public boolean supports(String type) {
        return registry.containsKey(type);
    }
}
