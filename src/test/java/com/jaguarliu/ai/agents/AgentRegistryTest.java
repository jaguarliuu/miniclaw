package com.jaguarliu.ai.agents;

import com.jaguarliu.ai.agents.model.AgentProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentRegistry 单元测试
 *
 * 测试覆盖：
 * 1. Profile 加载与获取
 * 2. 默认 Profile 回退
 * 3. 工具权限过滤（allow/deny）
 * 4. Agent 存在性验证
 * 5. Spawn 权限检查
 */
@DisplayName("AgentRegistry Tests")
class AgentRegistryTest {

    private AgentsProperties properties;
    private AgentRegistry registry;

    @BeforeEach
    void setUp() {
        properties = new AgentsProperties();
    }

    // ==================== 默认行为测试 ====================

    @Nested
    @DisplayName("Default Behavior")
    class DefaultBehaviorTests {

        @Test
        @DisplayName("无配置时自动创建默认 main profile")
        void createDefaultProfileWhenNoConfig() {
            // profiles 为空
            properties.setProfiles(new HashMap<>());
            registry = new AgentRegistry(properties);
            registry.init();

            assertTrue(registry.exists("main"));
            AgentProfile profile = registry.getDefault();
            assertNotNull(profile);
            assertEquals("main", profile.getId());
            assertEquals("trusted", profile.getSandbox());
            assertTrue(profile.isCanSpawn());
        }

        @Test
        @DisplayName("默认 agent ID 为 main")
        void defaultAgentIdIsMain() {
            properties.setProfiles(new HashMap<>());
            registry = new AgentRegistry(properties);
            registry.init();

            assertEquals("main", registry.getDefaultAgentId());
        }

        @Test
        @DisplayName("getOrDefault(null) 返回默认 profile")
        void getOrDefaultWithNullReturnsDefault() {
            properties.setProfiles(new HashMap<>());
            registry = new AgentRegistry(properties);
            registry.init();

            AgentProfile profile = registry.getOrDefault(null);
            assertEquals("main", profile.getId());
        }

        @Test
        @DisplayName("getOrDefault(blank) 返回默认 profile")
        void getOrDefaultWithBlankReturnsDefault() {
            properties.setProfiles(new HashMap<>());
            registry = new AgentRegistry(properties);
            registry.init();

            AgentProfile profile = registry.getOrDefault("   ");
            assertEquals("main", profile.getId());
        }
    }

    // ==================== Profile 加载测试 ====================

    @Nested
    @DisplayName("Profile Loading")
    class ProfileLoadingTests {

        @BeforeEach
        void setUpProfiles() {
            Map<String, AgentProfile> profiles = new HashMap<>();

            AgentProfile mainProfile = new AgentProfile();
            mainProfile.setSandbox("trusted");
            mainProfile.setCanSpawn(true);
            profiles.put("main", mainProfile);

            AgentProfile restrictedProfile = new AgentProfile();
            restrictedProfile.setSandbox("restricted");
            restrictedProfile.setCanSpawn(false);
            AgentProfile.ToolPermissions toolPerms = new AgentProfile.ToolPermissions();
            toolPerms.setAllow(Arrays.asList("read_file", "http_get"));
            toolPerms.setDeny(Arrays.asList("shell", "sessions_spawn"));
            restrictedProfile.setTools(toolPerms);
            profiles.put("restricted", restrictedProfile);

            properties.setProfiles(profiles);
            properties.setDefaultAgent("main");
            registry = new AgentRegistry(properties);
            registry.init();
        }

        @Test
        @DisplayName("加载配置中的所有 profile")
        void loadAllProfiles() {
            Set<String> agentIds = registry.listAgentIds();
            assertEquals(2, agentIds.size());
            assertTrue(agentIds.contains("main"));
            assertTrue(agentIds.contains("restricted"));
        }

        @Test
        @DisplayName("profile ID 与 key 一致")
        void profileIdMatchesKey() {
            AgentProfile main = registry.get("main").orElse(null);
            assertNotNull(main);
            assertEquals("main", main.getId());

            AgentProfile restricted = registry.get("restricted").orElse(null);
            assertNotNull(restricted);
            assertEquals("restricted", restricted.getId());
        }

        @Test
        @DisplayName("sandbox 配置正确加载")
        void sandboxConfigLoaded() {
            assertEquals("trusted", registry.get("main").get().getSandbox());
            assertEquals("restricted", registry.get("restricted").get().getSandbox());
        }

        @Test
        @DisplayName("get 不存在的 agentId 返回 empty")
        void getNonExistentReturnsEmpty() {
            Optional<AgentProfile> profile = registry.get("nonexistent");
            assertTrue(profile.isEmpty());
        }
    }

    // ==================== 工具权限测试 ====================

    @Nested
    @DisplayName("Tool Permissions")
    class ToolPermissionsTests {

        private AgentProfile profile;

        @BeforeEach
        void setUpProfile() {
            profile = new AgentProfile();
            AgentProfile.ToolPermissions perms = new AgentProfile.ToolPermissions();
            perms.setAllow(Arrays.asList("read_file", "write_file", "http_get"));
            perms.setDeny(Arrays.asList("shell", "sessions_spawn"));
            profile.setTools(perms);
        }

        @Test
        @DisplayName("allow 列表中的工具被允许")
        void allowedToolsAreAllowed() {
            assertTrue(profile.isToolAllowed("read_file"));
            assertTrue(profile.isToolAllowed("write_file"));
            assertTrue(profile.isToolAllowed("http_get"));
        }

        @Test
        @DisplayName("deny 列表中的工具被禁止")
        void deniedToolsAreDenied() {
            assertFalse(profile.isToolAllowed("shell"));
            assertFalse(profile.isToolAllowed("sessions_spawn"));
        }

        @Test
        @DisplayName("deny 优先级高于 allow")
        void denyTakesPrecedence() {
            // 同时在 allow 和 deny 中
            AgentProfile.ToolPermissions perms = new AgentProfile.ToolPermissions();
            perms.setAllow(Arrays.asList("shell", "read_file"));
            perms.setDeny(Arrays.asList("shell"));
            profile.setTools(perms);

            assertFalse(profile.isToolAllowed("shell"));
            assertTrue(profile.isToolAllowed("read_file"));
        }

        @Test
        @DisplayName("不在 allow 列表中的工具被禁止")
        void toolsNotInAllowAreDenied() {
            assertFalse(profile.isToolAllowed("memory_search"));
        }

        @Test
        @DisplayName("allow 为空时允许所有未 deny 的工具")
        void emptyAllowAllowsAll() {
            AgentProfile.ToolPermissions perms = new AgentProfile.ToolPermissions();
            perms.setAllow(Arrays.asList()); // 空列表
            perms.setDeny(Arrays.asList("shell"));
            profile.setTools(perms);

            assertTrue(profile.isToolAllowed("read_file"));
            assertTrue(profile.isToolAllowed("any_tool"));
            assertFalse(profile.isToolAllowed("shell"));
        }

        @Test
        @DisplayName("resolveAllowedTools 正确过滤")
        void resolveAllowedToolsFiltersCorrectly() {
            Set<String> available = new HashSet<>(Arrays.asList(
                    "read_file", "write_file", "shell", "http_get", "memory_search"
            ));

            Set<String> allowed = profile.resolveAllowedTools(available);

            assertTrue(allowed.contains("read_file"));
            assertTrue(allowed.contains("write_file"));
            assertTrue(allowed.contains("http_get"));
            assertFalse(allowed.contains("shell")); // denied
            assertFalse(allowed.contains("memory_search")); // not in allow
        }
    }

    // ==================== Spawn 权限测试 ====================

    @Nested
    @DisplayName("Spawn Permissions")
    class SpawnPermissionsTests {

        @BeforeEach
        void setUpProfiles() {
            Map<String, AgentProfile> profiles = new HashMap<>();

            AgentProfile mainProfile = new AgentProfile();
            mainProfile.setCanSpawn(true);
            profiles.put("main", mainProfile);

            AgentProfile restrictedProfile = new AgentProfile();
            restrictedProfile.setCanSpawn(false);
            profiles.put("restricted", restrictedProfile);

            properties.setProfiles(profiles);
            registry = new AgentRegistry(properties);
            registry.init();
        }

        @Test
        @DisplayName("canSpawn=true 的 agent 可以 spawn")
        void canSpawnTrueAllowsSpawn() {
            assertTrue(registry.canSpawn("main"));
        }

        @Test
        @DisplayName("canSpawn=false 的 agent 不能 spawn")
        void canSpawnFalseDeniesSpawn() {
            assertFalse(registry.canSpawn("restricted"));
        }

        @Test
        @DisplayName("默认 profile canSpawn=true")
        void defaultProfileCanSpawn() {
            properties.setProfiles(new HashMap<>());
            registry = new AgentRegistry(properties);
            registry.init();

            assertTrue(registry.canSpawn("main"));
        }
    }

    // ==================== 验证测试 ====================

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @BeforeEach
        void setUpProfiles() {
            Map<String, AgentProfile> profiles = new HashMap<>();
            profiles.put("main", new AgentProfile());
            profiles.put("public", new AgentProfile());
            properties.setProfiles(profiles);
            registry = new AgentRegistry(properties);
            registry.init();
        }

        @Test
        @DisplayName("isValidAgentId 对存在的 agent 返回 true")
        void validAgentIdReturnsTrue() {
            assertTrue(registry.isValidAgentId("main"));
            assertTrue(registry.isValidAgentId("public"));
        }

        @Test
        @DisplayName("isValidAgentId 对不存在的 agent 返回 false")
        void invalidAgentIdReturnsFalse() {
            assertFalse(registry.isValidAgentId("nonexistent"));
        }

        @Test
        @DisplayName("isValidAgentId 对 null 返回 true（使用默认）")
        void nullAgentIdIsValid() {
            assertTrue(registry.isValidAgentId(null));
        }

        @Test
        @DisplayName("isValidAgentId 对空字符串返回 true（使用默认）")
        void blankAgentIdIsValid() {
            assertTrue(registry.isValidAgentId(""));
            assertTrue(registry.isValidAgentId("   "));
        }

        @Test
        @DisplayName("exists 检查 agent 是否存在")
        void existsCheck() {
            assertTrue(registry.exists("main"));
            assertFalse(registry.exists("nonexistent"));
        }
    }

    // ==================== Lane 配置测试 ====================

    @Nested
    @DisplayName("Lane Configuration")
    class LaneConfigTests {

        @Test
        @DisplayName("默认 lane 配置")
        void defaultLaneConfig() {
            properties.setProfiles(new HashMap<>());
            registry = new AgentRegistry(properties);
            registry.init();

            AgentsProperties.LaneConfig laneConfig = registry.getLaneConfig();
            assertNotNull(laneConfig);
            assertEquals(4, laneConfig.getMainMaxConcurrency());
            assertEquals(8, laneConfig.getSubagentMaxConcurrency());
        }

        @Test
        @DisplayName("自定义 lane 配置")
        void customLaneConfig() {
            AgentsProperties.LaneConfig laneConfig = new AgentsProperties.LaneConfig();
            laneConfig.setMainMaxConcurrency(2);
            laneConfig.setSubagentMaxConcurrency(16);
            properties.setLane(laneConfig);

            properties.setProfiles(new HashMap<>());
            registry = new AgentRegistry(properties);
            registry.init();

            assertEquals(2, registry.getLaneConfig().getMainMaxConcurrency());
            assertEquals(16, registry.getLaneConfig().getSubagentMaxConcurrency());
        }
    }

    // ==================== AgentProfile 辅助方法测试 ====================

    @Nested
    @DisplayName("AgentProfile Helper Methods")
    class AgentProfileHelperTests {

        @Test
        @DisplayName("isRestricted 对 restricted sandbox 返回 true")
        void isRestrictedTrue() {
            AgentProfile profile = new AgentProfile();
            profile.setSandbox("restricted");
            assertTrue(profile.isRestricted());
        }

        @Test
        @DisplayName("isRestricted 对 trusted sandbox 返回 false")
        void isRestrictedFalse() {
            AgentProfile profile = new AgentProfile();
            profile.setSandbox("trusted");
            assertFalse(profile.isRestricted());
        }

        @Test
        @DisplayName("isRestricted 大小写不敏感")
        void isRestrictedCaseInsensitive() {
            AgentProfile profile = new AgentProfile();
            profile.setSandbox("RESTRICTED");
            assertTrue(profile.isRestricted());

            profile.setSandbox("Restricted");
            assertTrue(profile.isRestricted());
        }
    }

    // ==================== 默认 Agent 回退测试 ====================

    @Nested
    @DisplayName("Default Agent Fallback")
    class DefaultAgentFallbackTests {

        @Test
        @DisplayName("配置的 default-agent 不存在时回退到第一个")
        void fallbackToFirstWhenDefaultNotExists() {
            Map<String, AgentProfile> profiles = new HashMap<>();
            profiles.put("alpha", new AgentProfile());
            profiles.put("beta", new AgentProfile());
            properties.setProfiles(profiles);
            properties.setDefaultAgent("nonexistent");

            registry = new AgentRegistry(properties);
            registry.init();

            // 应该回退到某个存在的 agent
            String defaultAgentId = registry.getDefaultAgentId();
            assertTrue(registry.exists(defaultAgentId));
        }
    }
}
