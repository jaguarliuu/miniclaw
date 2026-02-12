package com.jaguarliu.ai.datasource.application.dto;

import com.jaguarliu.ai.datasource.domain.ConnectionConfig;
import com.jaguarliu.ai.datasource.domain.SecurityConfig;
import lombok.Builder;
import lombok.Getter;

/**
 * 更新数据源请求 DTO
 */
@Getter
@Builder
public class UpdateDataSourceRequest {

    private String name;

    private ConnectionConfig connectionConfig;

    private SecurityConfig securityConfig;
}
