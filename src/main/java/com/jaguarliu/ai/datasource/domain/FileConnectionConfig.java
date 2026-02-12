package com.jaguarliu.ai.datasource.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.Objects;

/**
 * 文件数据源连接配置
 * 值对象：不可变
 */
@Getter
public class FileConnectionConfig extends ConnectionConfig {

    private final String filePath;  // 文件路径（相对于 workspace）
    private final String encoding;  // 文件编码，默认 UTF-8
    private final Character delimiter;  // CSV 分隔符，默认逗号
    private final Boolean hasHeader;  // 是否有表头

    @JsonCreator
    public FileConnectionConfig(
            @JsonProperty("filePath") String filePath,
            @JsonProperty("encoding") String encoding,
            @JsonProperty("delimiter") Character delimiter,
            @JsonProperty("hasHeader") Boolean hasHeader) {
        this.filePath = filePath;
        this.encoding = encoding != null ? encoding : "UTF-8";
        this.delimiter = delimiter != null ? delimiter : ',';
        this.hasHeader = hasHeader != null ? hasHeader : true;
    }

    /**
     * 简化构造函数（仅文件路径）
     */
    public FileConnectionConfig(String filePath) {
        this(filePath, "UTF-8", ',', true);
    }

    @Override
    public boolean isValid() {
        return filePath != null && !filePath.isBlank();
    }

    @Override
    public String getDescription() {
        return filePath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileConnectionConfig that = (FileConnectionConfig) o;
        return Objects.equals(filePath, that.filePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath);
    }
}
