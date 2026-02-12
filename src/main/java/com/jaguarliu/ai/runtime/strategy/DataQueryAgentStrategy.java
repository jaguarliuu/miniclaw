package com.jaguarliu.ai.runtime.strategy;

import com.jaguarliu.ai.datasource.application.dto.DataSourceDTO;
import com.jaguarliu.ai.datasource.application.service.DataSourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * 数据查询 Agent 策略
 * 当请求携带 dataSourceId 且数据源为 JDBC 类型时激活
 */
@Slf4j
@Component
public class DataQueryAgentStrategy implements AgentStrategy {

    private final Optional<DataSourceService> dataSourceService;
    private final DataQueryPromptBuilder promptBuilder;

    /**
     * DataQuery Agent 可使用的工具白名单
     */
    private static final Set<String> ALLOWED_TOOLS = Set.of(
            "list_tables",
            "get_table_schema",
            "sample_data",
            "datasource_query",
            "write_file",
            "read_file",
            "shell",
            "use_skill"
    );

    public DataQueryAgentStrategy(Optional<DataSourceService> dataSourceService,
                                   DataQueryPromptBuilder promptBuilder) {
        this.dataSourceService = dataSourceService;
        this.promptBuilder = promptBuilder;
    }

    @Override
    public boolean supports(AgentContext context) {
        if (context.getDataSourceId() == null || context.getDataSourceId().isBlank()) {
            return false;
        }

        if (dataSourceService.isEmpty()) {
            return false;
        }

        try {
            DataSourceDTO ds = dataSourceService.get().getDataSource(context.getDataSourceId());
            if (ds == null) {
                return false;
            }
            // Only support JDBC data sources (not CSV/XLSX)
            return ds.getType().isJdbc();
        } catch (Exception e) {
            log.warn("Failed to check data source for strategy: {}", context.getDataSourceId(), e);
            return false;
        }
    }

    @Override
    public AgentExecutionPlan prepare(AgentContext context) {
        log.info("Using agent strategy: data-query (runId={}, dataSourceId={})",
                context.getRunId(), context.getDataSourceId());

        DataSourceDTO dataSource = dataSourceService.get().getDataSource(context.getDataSourceId());
        String systemPrompt = promptBuilder.build(context.getDataSourceId(), dataSource);

        return AgentExecutionPlan.builder()
                .systemPrompt(systemPrompt)
                .allowedTools(ALLOWED_TOOLS)
                .excludedMcpServers(context.getExcludedMcpServers())
                .maxStepsOverride(30)  // 多步查询 + 图表生成需要更多步数
                .strategyName("data-query")
                .build();
    }

    @Override
    public int priority() {
        return 10;
    }
}
