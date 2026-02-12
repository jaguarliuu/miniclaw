package com.jaguarliu.ai.datasource.domain;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 数据库 Schema 元数据
 * 实体对象
 */
@Getter
@Builder
public class SchemaMetadata {

    /** Schema 名称（数据库名） */
    private String schemaName;

    /** 表列表 */
    private List<TableMetadata> tables;

    /**
     * 表元数据
     */
    @Getter
    @Builder
    public static class TableMetadata {
        /** 表名 */
        private String tableName;

        /** 表注释 */
        private String comment;

        /** 列列表 */
        private List<ColumnMetadata> columns;

        /** 预估行数 */
        private Long rowCount;
    }

    /**
     * 列元数据
     */
    @Getter
    @Builder
    public static class ColumnMetadata {
        /** 列名 */
        private String columnName;

        /** 数据类型 */
        private String dataType;

        /** 是否可空 */
        private boolean nullable;

        /** 是否主键 */
        private boolean primaryKey;

        /** 列注释 */
        private String comment;

        /** 默认值 */
        private String defaultValue;
    }
}
