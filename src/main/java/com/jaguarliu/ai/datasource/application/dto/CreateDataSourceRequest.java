package com.jaguarliu.ai.datasource.application.dto;

import com.jaguarliu.ai.datasource.domain.ConnectionConfig;
import com.jaguarliu.ai.datasource.domain.DataSourceType;
import com.jaguarliu.ai.datasource.domain.SecurityConfig;
import lombok.Builder;
import lombok.Getter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建数据源请求 DTO
 */
@Getter
@Builder
public class CreateDataSourceRequest {

    @NotBlank(message = "数据源名称不能为空")
    private String name;

    @NotNull(message = "数据源类型不能为空")
    private DataSourceType type;

    @NotNull(message = "连接配置不能为空")
    private ConnectionConfig connectionConfig;

    private SecurityConfig securityConfig;
}
