package com.jaguarliu.ai.tools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {

    /**
     * 是否执行成功
     */
    private boolean success;

    /**
     * 结果内容（成功时为工具输出，失败时为错误信息）
     */
    private String content;

    /**
     * 创建成功结果
     */
    public static ToolResult success(String content) {
        return ToolResult.builder()
                .success(true)
                .content(content)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static ToolResult error(String message) {
        return ToolResult.builder()
                .success(false)
                .content("Error: " + message)
                .build();
    }
}
