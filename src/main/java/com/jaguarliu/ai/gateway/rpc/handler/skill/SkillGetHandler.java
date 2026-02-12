package com.jaguarliu.ai.gateway.rpc.handler.skill;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.skills.model.LoadedSkill;
import com.jaguarliu.ai.skills.registry.SkillRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * skills.get - 获取 skill 详情（包含正文）
 *
 * 请求格式：
 * { "name": "code-review" }
 *
 * 返回格式：
 * {
 *   "name": "code-review",
 *   "description": "代码审查",
 *   "body": "# Code Review\n...",
 *   "allowedTools": ["read_file", "grep"],
 *   "confirmBefore": ["write_file"]
 * }
 */
@Component
@RequiredArgsConstructor
public class SkillGetHandler implements RpcHandler {

    private final SkillRegistry skillRegistry;

    @Override
    public String getMethod() {
        return "skills.get";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        Map<String, Object> payload = (Map<String, Object>) request.getPayload();

        if (payload == null) {
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing payload"));
        }

        String name = (String) payload.get("name");
        if (name == null || name.isBlank()) {
            return Mono.just(RpcResponse.error(request.getId(), "INVALID_PARAMS", "Missing required field: name"));
        }

        Optional<LoadedSkill> skill = skillRegistry.activate(name);
        if (skill.isEmpty()) {
            return Mono.just(RpcResponse.error(request.getId(), "NOT_FOUND", "Skill not found or unavailable: " + name));
        }

        return Mono.just(RpcResponse.success(request.getId(), toDto(skill.get())));
    }

    private Map<String, Object> toDto(LoadedSkill skill) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("name", skill.getName());
        dto.put("description", skill.getDescription());
        dto.put("body", skill.getBody());
        dto.put("allowedTools", skill.getAllowedTools() != null ? skill.getAllowedTools() : List.of());
        dto.put("confirmBefore", skill.getConfirmBefore() != null ? skill.getConfirmBefore() : List.of());
        return dto;
    }
}
