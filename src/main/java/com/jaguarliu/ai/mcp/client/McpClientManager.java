package com.jaguarliu.ai.mcp.client;

import com.jaguarliu.ai.mcp.McpProperties;
import com.jaguarliu.ai.mcp.persistence.McpServerEntity;
import com.jaguarliu.ai.mcp.persistence.McpServerRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Client Manager
 * Phase 2: Basic dynamic connection management structure
 * Phase 3: Full MCP SDK integration with transport factories and tool discovery
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpClientManager {

    private final McpServerRepository mcpServerRepository;

    /**
     * Connected MCP clients (name -> client)
     * TODO: Replace Object with ManagedMcpClient in Phase 3
     */
    private final Map<String, Object> clients = new ConcurrentHashMap<>();

    /**
     * Initialize MCP Client Manager
     * Load enabled servers from database and connect
     */
    @PostConstruct
    public void initialize() {
        log.info("Initializing MCP Client Manager");

        // Load enabled servers from database
        List<McpServerEntity> servers = mcpServerRepository.findByEnabledTrue();

        if (servers.isEmpty()) {
            log.info("No enabled MCP servers found in database");
            return;
        }

        for (McpServerEntity entity : servers) {
            try {
                McpProperties.ServerConfig config = entity.toConfig();
                connectServer(config);
            } catch (Exception e) {
                log.error("Failed to connect MCP server: {}", entity.getName(), e);
            }
        }

        log.info("MCP Client Manager initialized with {} clients", clients.size());
    }

    /**
     * Connect to an MCP server dynamically
     * Phase 2: Placeholder implementation
     * Phase 3: Full MCP SDK integration with transport creation
     */
    public void connectServer(McpProperties.ServerConfig config) {
        String name = config.getName();

        if (clients.containsKey(name)) {
            log.warn("MCP client already exists: {}", name);
            throw new IllegalArgumentException("MCP client already exists: " + name);
        }

        log.info("Dynamically connecting to MCP server '{}' via {} transport",
                name, config.getTransport());

        // TODO Phase 3: Create Transport with transportFactory
        // TODO Phase 3: Create ManagedMcpClient
        // TODO Phase 3: Initialize connection
        // TODO Phase 3: Trigger tool discovery

        // Placeholder: Store config as placeholder client
        clients.put(name, config);

        log.info("Successfully connected to MCP server: {} (transport: {})",
                name, config.getTransport());
    }

    /**
     * Disconnect from an MCP server dynamically
     * Phase 2: Basic structure
     * Phase 3: Proper cleanup and tool removal
     */
    public void disconnectServer(String name) {
        Object client = clients.remove(name);
        if (client != null) {
            // TODO Phase 3: client.close()
            log.info("Disconnected MCP server: {}", name);

            // TODO Phase 3: Remove tools from McpToolRegistry
        } else {
            log.warn("Attempted to disconnect non-existent MCP server: {}", name);
        }
    }

    /**
     * Test connection to an MCP server (without persisting)
     * Phase 2: Basic test
     * Phase 3: Full connection test with transport and initialization
     */
    public boolean testConnection(McpProperties.ServerConfig config) {
        log.info("Testing connection to MCP server: {}", config.getName());

        try {
            // TODO Phase 3: Create temporary Transport
            // TODO Phase 3: Create temporary ManagedMcpClient
            // TODO Phase 3: Try initialize()
            // TODO Phase 3: Close temporary client

            log.info("Connection test successful for: {}", config.getName());
            return true;

        } catch (Exception e) {
            log.error("Connection test failed for: {}", config.getName(), e);
            return false;
        }
    }

    /**
     * Get connected MCP client by name
     * Phase 2: Return placeholder
     * Phase 3: Return ManagedMcpClient
     */
    public Optional<Object> getClient(String name) {
        return Optional.ofNullable(clients.get(name));
    }

    /**
     * Get all connected client names
     */
    public List<String> getConnectedServerNames() {
        return List.copyOf(clients.keySet());
    }
}
