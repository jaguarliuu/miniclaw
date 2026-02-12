package com.jaguarliu.ai.gateway.rpc.handler.skill;

import com.jaguarliu.ai.gateway.rpc.RpcHandler;
import com.jaguarliu.ai.gateway.rpc.model.RpcRequest;
import com.jaguarliu.ai.gateway.rpc.model.RpcResponse;
import com.jaguarliu.ai.skills.model.SkillEntry;
import com.jaguarliu.ai.skills.registry.SkillRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * skills.list - 获取所有 skill 列表
 *
 * 返回格式：
 * {
 *   "skills": [
 *     {
 *       "name": "code-review",
 *       "description": "代码审查",
 *       "available": true,
 *       "unavailableReason": "",
 *       "priority": 0,
 *       "tokenCost": 45
 *     }
 *   ],
 *   "version": 1
 * }
 */
@Component
@RequiredArgsConstructor
public class SkillListHandler implements RpcHandler {

    private final SkillRegistry skillRegistry;

    @Override
    public String getMethod() {
        return "skills.list";
    }

    @Override
    public Mono<RpcResponse> handle(String connectionId, RpcRequest request) {
        List<Map<String, Object>> skills = skillRegistry.getAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        Map<String, Object> payload = Map.of(
                "skills", skills,
                "version", skillRegistry.getSnapshotVersion()
        );

        return Mono.just(RpcResponse.success(request.getId(), payload));
    }

    private Map<String, Object> toDto(SkillEntry entry) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("name", entry.getMetadata().getName());
        dto.put("description", entry.getMetadata().getDescription());
        dto.put("available", entry.isAvailable());
        dto.put("unavailableReason", entry.getUnavailableReason() != null ? entry.getUnavailableReason() : "");
        dto.put("priority", entry.getMetadata().getPriority());
        dto.put("tokenCost", entry.getTokenCost());
        return dto;
    }
}
