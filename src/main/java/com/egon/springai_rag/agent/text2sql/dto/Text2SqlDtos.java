package com.egon.springai_rag.agent.text2sql.dto;

import java.util.List;
import java.util.Map;

/**
 * text2SQL DTO 容器。
 */
public interface Text2SqlDtos {

    /**
     * text2SQL 查询请求。
     */
    public record Text2SqlRequest(String question) {}

    /**
     * text2SQL 查询响应。
     */
    public record Text2SqlResponse(
            String question,
            String sql,
            List<Map<String, Object>> results,
            int rowCount,
            String summary,
            long executionTimeMs
    ) {}

    /**
     * 数据库 Schema 信息。
     */
    public record SchemaInfo(
            String tableName,
            List<ColumnInfo> columns,
            List<String> primaryKeys,
            List<ForeignKeyInfo> foreignKeys
    ) {
        public record ColumnInfo(
                String columnName,
                String dataType,
                boolean nullable,
                String defaultValue
        ) {}

        public record ForeignKeyInfo(
                String columnName,
                String referencedTable,
                String referencedColumn
        ) {}
    }

    /**
     * 历史查询记录。
     */
    public record QueryHistoryRecord(
            String question,
            String sql,
            String resultSummary,
            double similarityScore
    ) {}

    /**
     * SQL 校验结果。
     */
    public record ValidationResult(
            boolean isValid,
            String errorMessage,
            List<String> blockedReasons
    ) {}
}