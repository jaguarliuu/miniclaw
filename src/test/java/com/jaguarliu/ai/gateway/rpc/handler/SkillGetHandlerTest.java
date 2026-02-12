package com.jaguarliu.ai.gateway.rpc.handler;

import com.jaguarliu.ai.gateway.rpc.handler.skill.SkillGetHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.skills.model.LoadedSkill;
import com.jaguarliu.ai.skills.registry.SkillRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * SkillGetHandler 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SkillGetHandler Tests")
class SkillGetHandlerTest {

    @Mock
    private SkillRegistry skillRegistry;

    @InjectMocks
    private SkillGetHandler handler;

    @Test
    @DisplayName("返回正确的方法名")
    void getMethod() {
        assertEquals("skills.get", handler.getMethod());
    }

    @Test
    @DisplayName("成功获取 skill 详情")
    @SuppressWarnings("unchecked")
    void handleSuccess() {
        LoadedSkill skill = LoadedSkill.builder()
                .name("code-review")
                .description("代码审查")
                .body("# Code Review\n请审查以下代码：$ARGUMENTS")
                .allowedTools(Set.of("read_file", "grep"))
                .confirmBefore(Set.of("write_file"))
                .build();

        when(skillRegistry.activate("code-review")).thenReturn(Optional.of(skill));

        RpcRequest request = RpcRequest.builder()
                .id("req-1")
                .method("skills.get")
                .payload(Map.of("name", "code-review"))
                .build();

        RpcResponse response = handler.handle("conn-1", request).block();

        assertNotNull(response);
        assertEquals("response", response.getType());
        assertNull(response.getError());

        Map<String, Object> payload = (Map<String, Object>) response.getPayload();
        assertEquals("code-review", payload.get("name"));
        assertEquals("代码审查", payload.get("description"));
        assertEquals("# Code Review\n请审查以下代码：$ARGUMENTS", payload.get("body"));
        assertTrue(((Set<?>) payload.get("allowedTools")).contains("read_file"));
        assertTrue(((Set<?>) payload.get("confirmBefore")).contains("write_file"));
    }

    @Test
    @DisplayName("skill 不存在时返回错误")
    void handleNotFound() {
        when(skillRegistry.activate("nonexistent")).thenReturn(Optional.empty());

        RpcRequest request = RpcRequest.builder()
                .id("req-1")
                .method("skills.get")
                .payload(Map.of("name", "nonexistent"))
                .build();

        RpcResponse response = handler.handle("conn-1", request).block();

        assertNotNull(response);
        assertNotNull(response.getError());
        assertEquals("NOT_FOUND", response.getError().getCode());
    }

    @Test
    @DisplayName("缺少 name 参数时返回错误")
    void handleMissingName() {
        RpcRequest request = RpcRequest.builder()
                .id("req-1")
                .method("skills.get")
                .payload(Map.of())
                .build();

        RpcResponse response = handler.handle("conn-1", request).block();

        assertNotNull(response);
        assertNotNull(response.getError());
        assertEquals("INVALID_PARAMS", response.getError().getCode());
    }

    @Test
    @DisplayName("payload 为空时返回错误")
    void handleNullPayload() {
        RpcRequest request = RpcRequest.builder()
                .id("req-1")
                .method("skills.get")
                .payload(null)
                .build();

        RpcResponse response = handler.handle("conn-1", request).block();

        assertNotNull(response);
        assertNotNull(response.getError());
        assertEquals("INVALID_PARAMS", response.getError().getCode());
    }

    @Test
    @DisplayName("allowedTools 为 null 时返回空列表")
    @SuppressWarnings("unchecked")
    void handleNullAllowedTools() {
        LoadedSkill skill = LoadedSkill.builder()
                .name("simple")
                .description("简单 skill")
                .body("body")
                .allowedTools(null)
                .confirmBefore(null)
                .build();

        when(skillRegistry.activate("simple")).thenReturn(Optional.of(skill));

        RpcRequest request = RpcRequest.builder()
                .id("req-1")
                .method("skills.get")
                .payload(Map.of("name", "simple"))
                .build();

        RpcResponse response = handler.handle("conn-1", request).block();

        assertNotNull(response);
        Map<String, Object> payload = (Map<String, Object>) response.getPayload();
        assertEquals(java.util.List.of(), payload.get("allowedTools"));
        assertEquals(java.util.List.of(), payload.get("confirmBefore"));
    }
}
