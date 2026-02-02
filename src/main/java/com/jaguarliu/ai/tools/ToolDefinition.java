package com.jaguarliu.ai.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工具定义
 * 描述一个工具的元数据，供 LLM Function Calling 使用
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolDefinition {

    /**
     * 工具名称（唯一标识）
     */
    private String name;

    /**
     * 工具描述（给 LLM 看，说明工具用途）
     */
    private String description;

    /**
     * 参数定义（JSON Schema 格式）
     * 示例：
     * {
     *   "type": "object",
     *   "properties": {
     *     "path": { "type": "string", "description": "文件路径" }
     *   },
     *   "required": ["path"]
     * }
     */
    private Map<String, Object> parameters;

    /**
     * 是否需要 HITL（Human-in-the-Loop）确认
     * true = 执行前需要用户确认
     */
    @Builder.Default
    private boolean hitl = false;

    /**
     * 转换为 OpenAI Function Calling 格式
     */
    public Map<String, Object> toOpenAiFormat() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description,
                        "parameters", parameters != null ? parameters : Map.of("type", "object", "properties", Map.of())
                )
        );
    }
}
