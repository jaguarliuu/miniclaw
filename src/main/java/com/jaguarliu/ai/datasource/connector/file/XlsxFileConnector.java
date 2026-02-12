package com.jaguarliu.ai.datasource.connector.file;

import com.jaguarliu.ai.datasource.connector.AbstractDataSourceConnector;
import com.jaguarliu.ai.datasource.connector.ConnectorException;
import com.jaguarliu.ai.datasource.domain.FileConnectionConfig;
import com.jaguarliu.ai.datasource.domain.QueryResult;
import com.jaguarliu.ai.datasource.domain.SchemaMetadata;
import com.jaguarliu.ai.datasource.domain.SecurityConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * XLSX (Excel) 文件连接器
 *
 * 使用 Apache POI 解析
 */
@Slf4j
public class XlsxFileConnector extends AbstractDataSourceConnector {

    private final FileConnectionConfig fileConfig;
    private Path filePath;

    public XlsxFileConnector(
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
            try (FileInputStream fis = new FileInputStream(filePath.toFile());
                 Workbook workbook = new XSSFWorkbook(fis)) {
                // 只是验证能否打开
                if (workbook.getNumberOfSheets() == 0) {
                    throw new ConnectorException(
                            "Excel file has no sheets",
                            ConnectorException.ErrorType.FILE_FORMAT_ERROR);
                }
            }

            this.connected = true;
            log.info("Successfully connected to Excel file: {}", fileConfig.getFilePath());

        } catch (IOException e) {
            log.error("Failed to connect to Excel file: {}", e.getMessage(), e);
            throw new ConnectorException(
                    "Failed to read Excel file: " + e.getMessage(),
                    e,
                    ConnectorException.ErrorType.FILE_FORMAT_ERROR);
        }
    }

    @Override
    public void disconnect() throws ConnectorException {
        this.connected = false;
        log.info("Disconnected from Excel file: {}", fileConfig.getFilePath());
    }

    @Override
    public QueryResult executeQuery(String query, int maxRows, int timeoutSeconds)
            throws ConnectorException {
        ensureConnected();

        long startTime = System.currentTimeMillis();
        int effectiveMaxRows = getEffectiveMaxRows(maxRows);

        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             Workbook workbook = new XSSFWorkbook(fis)) {

            // 读取第一个 sheet
            Sheet sheet = workbook.getSheetAt(0);

            if (sheet.getPhysicalNumberOfRows() == 0) {
                return QueryResult.success(List.of(), List.of(), 0, false);
            }

            // 读取表头
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            List<String> columns = new ArrayList<>();

            if (fileConfig.getHasHeader() && headerRow != null) {
                for (Cell cell : headerRow) {
                    columns.add(getCellValueAsString(cell));
                }
            } else {
                // 如果没有表头，使用列索引
                int columnCount = headerRow != null ? headerRow.getLastCellNum() : 0;
                for (int i = 0; i < columnCount; i++) {
                    columns.add("Column" + (i + 1));
                }
            }

            // 读取数据行
            List<Map<String, Object>> rows = new ArrayList<>();
            boolean truncated = false;
            int rowCount = 0;
            int startRowIndex = fileConfig.getHasHeader() ? 1 : 0;

            for (int i = sheet.getFirstRowNum() + startRowIndex; i <= sheet.getLastRowNum(); i++) {
                if (rowCount >= effectiveMaxRows) {
                    truncated = true;
                    break;
                }

                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }

                Map<String, Object> rowData = new LinkedHashMap<>();
                for (int j = 0; j < columns.size(); j++) {
                    Cell cell = row.getCell(j);
                    Object value = cell != null ? getCellValue(cell) : null;
                    rowData.put(columns.get(j), value);
                }
                rows.add(rowData);
                rowCount++;
            }

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Excel read completed. Rows: {}, Time: {}ms, Truncated: {}",
                    rowCount, executionTime, truncated);

            return QueryResult.success(columns, rows, executionTime, truncated);

        } catch (IOException e) {
            log.error("Failed to read Excel file: {}", e.getMessage(), e);
            throw new ConnectorException(
                    "Failed to read Excel: " + e.getMessage(),
                    e,
                    ConnectorException.ErrorType.FILE_FORMAT_ERROR);
        }
    }

    @Override
    public SchemaMetadata getSchemaMetadata() throws ConnectorException {
        ensureConnected();

        try (FileInputStream fis = new FileInputStream(filePath.toFile());
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());

            List<SchemaMetadata.ColumnMetadata> columns = new ArrayList<>();

            if (headerRow != null) {
                for (Cell cell : headerRow) {
                    String columnName = fileConfig.getHasHeader()
                            ? getCellValueAsString(cell)
                            : "Column" + (cell.getColumnIndex() + 1);

                    columns.add(SchemaMetadata.ColumnMetadata.builder()
                            .columnName(columnName)
                            .dataType("TEXT")
                            .nullable(true)
                            .primaryKey(false)
                            .build());
                }
            }

            // 计算行数（不包括表头）
            long rowCount = sheet.getPhysicalNumberOfRows();
            if (fileConfig.getHasHeader() && rowCount > 0) {
                rowCount--;
            }

            SchemaMetadata.TableMetadata table = SchemaMetadata.TableMetadata.builder()
                    .tableName(sheet.getSheetName())
                    .comment("Excel Sheet")
                    .columns(columns)
                    .rowCount(rowCount)
                    .build();

            return SchemaMetadata.builder()
                    .schemaName(filePath.getFileName().toString())
                    .tables(List.of(table))
                    .build();

        } catch (IOException e) {
            log.error("Failed to get Excel metadata: {}", e.getMessage(), e);
            throw new ConnectorException(
                    "Failed to read Excel metadata: " + e.getMessage(),
                    e,
                    ConnectorException.ErrorType.FILE_FORMAT_ERROR);
        }
    }

    @Override
    public String getConnectorType() {
        return "XLSX-FILE";
    }

    /**
     * 获取单元格值
     */
    private Object getCellValue(Cell cell) {
        if (cell == null) {
            return null;
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue();
                } else {
                    yield cell.getNumericCellValue();
                }
            }
            case BOOLEAN -> cell.getBooleanCellValue();
            case FORMULA -> cell.getCellFormula();
            case BLANK -> null;
            default -> cell.toString();
        };
    }

    /**
     * 获取单元格值作为字符串
     */
    private String getCellValueAsString(Cell cell) {
        Object value = getCellValue(cell);
        return value != null ? value.toString() : "";
    }
}
