package com.jaguarliu.ai.datasource.domain;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 查询结果
 * 值对象
 */
@Getter
@Builder
public class QueryResult {

    /** 列名列表 */
    private List<String> columns;

    /** 数据行（每行是一个 Map，key 为列名） */
    private List<Map<String, Object>> rows;

    /** 总行数 */
    private int rowCount;

    /** 执行时间（毫秒） */
    private long executionTime;

    /** 是否被截断（因为超过最大行数限制） */
    private boolean truncated;

    /** 错误信息（如果查询失败） */
    private String error;

    /**
     * 创建成功的查询结果
     */
    public static QueryResult success(List<String> columns, List<Map<String, Object>> rows,
                                      long executionTime, boolean truncated) {
        return QueryResult.builder()
                .columns(columns)
                .rows(rows)
                .rowCount(rows.size())
                .executionTime(executionTime)
                .truncated(truncated)
                .build();
    }

    /**
     * 创建失败的查询结果
     */
    public static QueryResult failure(String error) {
        return QueryResult.builder()
                .error(error)
                .rowCount(0)
                .build();
    }

    /**
     * 是否成功
     */
    public boolean isSuccess() {
        return error == null;
    }
}
