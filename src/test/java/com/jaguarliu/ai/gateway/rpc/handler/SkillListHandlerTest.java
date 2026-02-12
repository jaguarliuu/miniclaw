package com.jaguarliu.ai.gateway.rpc.handler;

import com.jaguarliu.ai.gateway.rpc.handler.skill.SkillListHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.skills.model.SkillEntry;
import com.jaguarliu.ai.skills.model.SkillMetadata;
import com.jaguarliu.ai.skills.registry.SkillRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * SkillListHandler 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SkillListHandler Tests")
class SkillListHandlerTest {

    @Mock
    private SkillRegistry skillRegistry;

    @InjectMocks
    private SkillListHandler handler;

    @Test
    @DisplayName("返回正确的方法名")
    void getMethod() {
        assertEquals("skills.list", handler.getMethod());
    }

    @Test
    @DisplayName("返回所有 skill 列表")
    @SuppressWarnings("unchecked")
    void handleReturnsSkillList() {
        SkillEntry entry1 = SkillEntry.builder()
                .metadata(SkillMetadata.builder()
                        .name("code-review")
                        .description("代码审查")
                        .priority(0)
                        .build())
                .available(true)
                .tokenCost(45)
                .build();

        SkillEntry entry2 = SkillEntry.builder()
                .metadata(SkillMetadata.builder()
                        .name("git-commit")
                        .description("生成 commit message")
                        .priority(1)
                        .build())
                .available(false)
                .unavailableReason("Missing binary: git")
                .tokenCost(30)
                .build();

        when(skillRegistry.getAll()).thenReturn(List.of(entry1, entry2));
        when(skillRegistry.getSnapshotVersion()).thenReturn(5L);

        RpcRequest request = RpcRequest.builder()
                .id("req-1")
                .method("skills.list")
                .build();

        RpcResponse response = handler.handle("conn-1", request).block();

        assertNotNull(response);
        assertEquals("response", response.getType());
        assertEquals("req-1", response.getId());
        assertNull(response.getError());

        Map<String, Object> payload = (Map<String, Object>) response.getPayload();
        assertNotNull(payload);
        assertEquals(5L, payload.get("version"));

        List<Map<String, Object>> skills = (List<Map<String, Object>>) payload.get("skills");
        assertEquals(2, skills.size());

        // 验证第一个 skill
        Map<String, Object> skill1 = skills.get(0);
        assertEquals("code-review", skill1.get("name"));
        assertEquals("代码审查", skill1.get("description"));
        assertEquals(true, skill1.get("available"));
        assertEquals("", skill1.get("unavailableReason"));
        assertEquals(0, skill1.get("priority"));
        assertEquals(45, skill1.get("tokenCost"));

        // 验证第二个 skill
        Map<String, Object> skill2 = skills.get(1);
        assertEquals("git-commit", skill2.get("name"));
        assertEquals(false, skill2.get("available"));
        assertEquals("Missing binary: git", skill2.get("unavailableReason"));
    }

    @Test
    @DisplayName("空列表返回空数组")
    @SuppressWarnings("unchecked")
    void handleEmptyList() {
        when(skillRegistry.getAll()).thenReturn(List.of());
        when(skillRegistry.getSnapshotVersion()).thenReturn(1L);

        RpcRequest request = RpcRequest.builder()
                .id("req-1")
                .method("skills.list")
                .build();

        RpcResponse response = handler.handle("conn-1", request).block();

        assertNotNull(response);
        Map<String, Object> payload = (Map<String, Object>) response.getPayload();
        List<Map<String, Object>> skills = (List<Map<String, Object>>) payload.get("skills");
        assertTrue(skills.isEmpty());
    }
}
