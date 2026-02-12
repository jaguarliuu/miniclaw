package com.jaguarliu.ai.soul;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Soul 配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoulConfigService {

    private final SoulConfigRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * 获取当前激活的 Soul 配置
     */
    public Optional<SoulConfigEntity> getActiveConfig() {
        return repository.findFirstByEnabledTrueOrderByUpdatedAtDesc();
    }

    /**
     * 获取配置（转换为 Map）
     */
    public Map<String, Object> getConfig() {
        Optional<SoulConfigEntity> config = getActiveConfig();

        if (config.isEmpty()) {
            return getDefaultConfig();
        }

        SoulConfigEntity entity = config.get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entity.getId());
        result.put("agentName", entity.getAgentName());
        result.put("personality", entity.getPersonality());
        result.put("traits", parseJsonArray(entity.getTraits()));
        result.put("responseStyle", entity.getResponseStyle());
        result.put("detailLevel", entity.getDetailLevel());
        result.put("expertise", parseJsonArray(entity.getExpertise()));
        result.put("forbiddenTopics", parseJsonArray(entity.getForbiddenTopics()));
        result.put("customPrompt", entity.getCustomPrompt());
        result.put("enabled", entity.isEnabled());

        return result;
    }

    /**
     * 保存配置
     */
    @Transactional
    public void saveConfig(Map<String, Object> configMap) {
        // 禁用所有现有配置
        List<SoulConfigEntity> all = repository.findAll();
        all.forEach(e -> e.setEnabled(false));
        repository.saveAll(all);

        // 创建新配置
        SoulConfigEntity entity = new SoulConfigEntity();
        entity.setAgentName((String) configMap.get("agentName"));
        entity.setPersonality((String) configMap.get("personality"));
        entity.setTraits(toJsonArray(configMap.get("traits")));
        entity.setResponseStyle((String) configMap.get("responseStyle"));
        entity.setDetailLevel((String) configMap.get("detailLevel"));
        entity.setExpertise(toJsonArray(configMap.get("expertise")));
        entity.setForbiddenTopics(toJsonArray(configMap.get("forbiddenTopics")));
        entity.setCustomPrompt((String) configMap.get("customPrompt"));
        entity.setEnabled(true);

        repository.save(entity);
        log.info("Soul config saved: {}", entity.getAgentName());
    }

    /**
     * 生成系统提示词片段
     */
    public String generateSystemPrompt() {
        Optional<SoulConfigEntity> config = getActiveConfig();

        if (config.isEmpty()) {
            return "";
        }

        SoulConfigEntity soul = config.get();
        StringBuilder prompt = new StringBuilder();

        prompt.append("# Your Identity\n\n");

        if (soul.getAgentName() != null && !soul.getAgentName().isEmpty()) {
            prompt.append("Your name is ").append(soul.getAgentName()).append(".\n\n");
        }

        if (soul.getPersonality() != null && !soul.getPersonality().isEmpty()) {
            prompt.append("## Personality\n");
            prompt.append(soul.getPersonality()).append("\n\n");
        }

        List<String> traits = parseJsonArray(soul.getTraits());
        if (!traits.isEmpty()) {
            prompt.append("## Key Traits\n");
            traits.forEach(trait -> prompt.append("- ").append(trait).append("\n"));
            prompt.append("\n");
        }

        List<String> expertise = parseJsonArray(soul.getExpertise());
        if (!expertise.isEmpty()) {
            prompt.append("## Areas of Expertise\n");
            expertise.forEach(area -> prompt.append("- ").append(area).append("\n"));
            prompt.append("\n");
        }

        if (soul.getResponseStyle() != null) {
            prompt.append("## Response Style\n");
            prompt.append("Tone: ").append(soul.getResponseStyle()).append("\n");
        }

        if (soul.getDetailLevel() != null) {
            prompt.append("Detail Level: ").append(soul.getDetailLevel()).append("\n\n");
        }

        List<String> forbidden = parseJsonArray(soul.getForbiddenTopics());
        if (!forbidden.isEmpty()) {
            prompt.append("## Topics to Avoid\n");
            forbidden.forEach(topic -> prompt.append("- ").append(topic).append("\n"));
            prompt.append("\n");
        }

        if (soul.getCustomPrompt() != null && !soul.getCustomPrompt().isEmpty()) {
            prompt.append("## Additional Guidelines\n");
            prompt.append(soul.getCustomPrompt()).append("\n\n");
        }

        return prompt.toString();
    }

    // Helper methods
    private List<String> parseJsonArray(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse JSON array: {}", json);
            return Collections.emptyList();
        }
    }

    private String toJsonArray(Object obj) {
        if (obj == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize to JSON: {}", obj);
            return "[]";
        }
    }

    private Map<String, Object> getDefaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("agentName", "MiniClaw");
        config.put("personality", "A helpful and professional AI assistant");
        config.put("traits", Collections.emptyList());
        config.put("responseStyle", "balanced");
        config.put("detailLevel", "balanced");
        config.put("expertise", Collections.emptyList());
        config.put("forbiddenTopics", Collections.emptyList());
        config.put("customPrompt", "");
        config.put("enabled", false);
        return config;
    }
}
