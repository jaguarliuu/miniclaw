package com.jaguarliu.ai.mcp.client;

import com.jaguarliu.ai.mcp.McpProperties;
import com.jaguarliu.ai.mcp.persistence.McpServerEntity;
import com.jaguarliu.ai.mcp.persistence.McpServerRepository;
import com.jaguarliu.ai.mcp.transport.McpTransportFactory;
import io.modelcontextprotocol.spec.McpClientTransport;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Client Manager
 * Manages MCP client lifecycle: connection, disconnection, and client access
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpClientManager {

    private final McpServerRepository mcpServerRepository;
    private final McpTransportFactory transportFactory;

    /**
     * Connected MCP clients (name -> client)
     */
    private final Map<String, ManagedMcpClient> clients = new ConcurrentHashMap<>();

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
     */
    public void connectServer(McpProperties.ServerConfig config) {
        String name = config.getName();

        if (clients.containsKey(name)) {
            log.warn("MCP client already exists: {}", name);
            throw new IllegalArgumentException("MCP client already exists: " + name);
        }

        log.info("Dynamically connecting to MCP server '{}' via {} transport",
                name, config.getTransport());

        try {
            // Create Transport
            McpClientTransport transport = transportFactory.createTransport(config);

            // Create ManagedMcpClient
            ManagedMcpClient managedClient = ManagedMcpClient.create(config, transport);

            // Initialize connection
            managedClient.initialize();

            // Store client
            clients.put(name, managedClient);

            log.info("Successfully connected to MCP server: {} (transport: {})",
                    name, config.getTransport());

        } catch (Exception e) {
            log.error("Failed to connect to MCP server: {}", name, e);
            throw new RuntimeException("Failed to connect to MCP server: " + name, e);
        }
    }

    /**
     * Disconnect from an MCP server dynamically
     */
    public void disconnectServer(String name) {
        ManagedMcpClient client = clients.remove(name);
        if (client != null) {
            try {
                client.close();
                log.info("Disconnected MCP server: {}", name);
            } catch (Exception e) {
                log.error("Error disconnecting MCP server: {}", name, e);
            }
        } else {
            log.warn("Attempted to disconnect non-existent MCP server: {}", name);
        }
    }

    /**
     * Test connection to an MCP server (without persisting)
     */
    public boolean testConnection(McpProperties.ServerConfig config) {
        log.info("Testing connection to MCP server: {}", config.getName());

        ManagedMcpClient tempClient = null;
        try {
            // Create temporary Transport
            McpClientTransport transport = transportFactory.createTransport(config);

            // Create temporary ManagedMcpClient
            tempClient = ManagedMcpClient.create(config, transport);

            // Try initialize()
            tempClient.initialize();

            log.info("Connection test successful for: {}", config.getName());
            return true;

        } catch (Exception e) {
            log.error("Connection test failed for: {}", config.getName(), e);
            return false;

        } finally {
            // Close temporary client
            if (tempClient != null) {
                try {
                    tempClient.close();
                } catch (Exception e) {
                    log.warn("Error closing temporary test client: {}", config.getName(), e);
                }
            }
        }
    }

    /**
     * Get connected MCP client by name
     */
    public Optional<ManagedMcpClient> getClient(String name) {
        return Optional.ofNullable(clients.get(name));
    }

    /**
     * Get all connected clients
     */
    public List<ManagedMcpClient> getAllClients() {
        return new ArrayList<>(clients.values());
    }

    /**
     * Get all connected client names
     */
    public List<String> getConnectedServerNames() {
        return List.copyOf(clients.keySet());
    }
}
