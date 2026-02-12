package com.jaguarliu.ai.datasource.connector.file;

import com.jaguarliu.ai.datasource.connector.AbstractDataSourceConnector;
import com.jaguarliu.ai.datasource.connector.ConnectorException;
import com.jaguarliu.ai.datasource.domain.FileConnectionConfig;
import com.jaguarliu.ai.datasource.domain.QueryResult;
import com.jaguarliu.ai.datasource.domain.SchemaMetadata;
import com.jaguarliu.ai.datasource.domain.SecurityConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * CSV 文件连接器
 *
 * 使用 Apache Commons CSV 解析
 */
@Slf4j
public class CsvFileConnector extends AbstractDataSourceConnector {

    private final FileConnectionConfig fileConfig;
    private Path filePath;

    public CsvFileConnector(
            FileConnectionConfig fileConfig,
            SecurityConfig securityConfig) {
        super(fileConfig, securityConfig);
        this.fileConfig = fileConfig;
    }

    @Override
    public void connect() throws ConnectorException {
        if (connected) {
            return;
        }

        try {
            // 验证文件路径
            filePath = Paths.get(fileConfig.getFilePath());

            if (!Files.exists(filePath)) {
                throw new ConnectorException(
                        "File not found: " + fileConfig.getFilePath(),
                        ConnectorException.ErrorType.RESOURCE_NOT_FOUND);
            }

            if (!Files.isReadable(filePath)) {
                throw new ConnectorException(
                        "File not readable: " + fileConfig.getFilePath(),
                        ConnectorException.ErrorType.PERMISSION_DENIED);
            }

            // 验证文件格式
            try (FileReader reader = new FileReader(filePath.toFile());
                 CSVParser parser = createParser(reader)) {
                // 只是验证能否解析
                parser.iterator().hasNext();
            }

            this.connected = true;
            log.info("Successfully connected to CSV file: {}", fileConfig.getFilePath());

        } catch (IOException e) {
            log.error("Failed to connect to CSV file: {}", e.getMessage(), e);
            throw new ConnectorException(
                    "Failed to read CSV file: " + e.getMessage(),
                    e,
                    ConnectorException.ErrorType.FILE_FORMAT_ERROR);
        }
    }

    @Override
    public void disconnect() throws ConnectorException {
        this.connected = false;
        log.info("Disconnected from CSV file: {}", fileConfig.getFilePath());
    }

    @Override
    public QueryResult executeQuery(String query, int maxRows, int timeoutSeconds)
            throws ConnectorException {
        ensureConnected();

        long startTime = System.currentTimeMillis();
        int effectiveMaxRows = getEffectiveMaxRows(maxRows);

        try (FileReader reader = new FileReader(filePath.toFile());
             CSVParser parser = createParser(reader)) {

            List<String> columns = new ArrayList<>(parser.getHeaderNames());
            List<Map<String, Object>> rows = new ArrayList<>();
            boolean truncated = false;
            int rowCount = 0;

            for (CSVRecord record : parser) {
                if (rowCount >= effectiveMaxRows) {
                    truncated = true;
                    break;
                }

                Map<String, Object> row = new LinkedHashMap<>();
                for (String column : columns) {
                    row.put(column, record.get(column));
                }
                rows.add(row);
                rowCount++;
            }

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("CSV read completed. Rows: {}, Time: {}ms, Truncated: {}",
                    rowCount, executionTime, truncated);

            return QueryResult.success(columns, rows, executionTime, truncated);

        } catch (IOException e) {
            log.error("Failed to read CSV file: {}", e.getMessage(), e);
            throw new ConnectorException(
                    "Failed to read CSV: " + e.getMessage(),
                    e,
                    ConnectorException.ErrorType.FILE_FORMAT_ERROR);
        }
    }

    @Override
    public SchemaMetadata getSchemaMetadata() throws ConnectorException {
        ensureConnected();

        try (FileReader reader = new FileReader(filePath.toFile());
             CSVParser parser = createParser(reader)) {

            List<String> headers = new ArrayList<>(parser.getHeaderNames());
            List<SchemaMetadata.ColumnMetadata> columns = new ArrayList<>();

            for (String header : headers) {
                columns.add(SchemaMetadata.ColumnMetadata.builder()
                        .columnName(header)
                        .dataType("TEXT")
                        .nullable(true)
                        .primaryKey(false)
                        .build());
            }

            // 估算行数
            long rowCount = parser.stream().count();

            SchemaMetadata.TableMetadata table = SchemaMetadata.TableMetadata.builder()
                    .tableName(filePath.getFileName().toString())
                    .comment("CSV File")
                    .columns(columns)
                    .rowCount(rowCount)
                    .build();

            return SchemaMetadata.builder()
                    .schemaName(filePath.getFileName().toString())
                    .tables(List.of(table))
                    .build();

        } catch (IOException e) {
            log.error("Failed to get CSV metadata: {}", e.getMessage(), e);
            throw new ConnectorException(
                    "Failed to read CSV metadata: " + e.getMessage(),
                    e,
                    ConnectorException.ErrorType.FILE_FORMAT_ERROR);
        }
    }

    @Override
    public String getConnectorType() {
        return "CSV-FILE";
    }

    /**
     * 创建 CSV 解析器
     */
    private CSVParser createParser(FileReader reader) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(fileConfig.getDelimiter())
                .setHeader()
                .setSkipHeaderRecord(fileConfig.getHasHeader())
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        return new CSVParser(reader, format);
    }
}
