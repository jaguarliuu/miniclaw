package com.jaguarliu.ai.tools;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 工具配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "tools")
public class ToolsProperties {

    /**
     * 工作空间目录（工具只能访问此目录内的文件）
     */
    private String workspace = "./workspace";

    /**
     * 最大文件大小（字节）
     */
    private long maxFileSize = 1048576; // 1MB
}
