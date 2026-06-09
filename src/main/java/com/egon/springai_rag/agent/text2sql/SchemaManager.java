package com.egon.springai_rag.agent.text2sql;

import com.egon.springai_rag.agent.text2sql.dto.Text2SqlDtos.SchemaInfo;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Schema 管理器 — 从 JDBC DatabaseMetaData 自动发现并缓存表结构。
 * <p>
 * 职责：
 * <ul>
 *   <li>启动时自动读取所有表的列、主键、外键信息</li>
 *   <li>生成 DDL 文本用于 prompt</li>
 *   <li>按白名单过滤表</li>
 * </ul>
 */
@Slf4j
@Component
public class SchemaManager {

    private final DataSource dataSource;
    private final Set<String> allowedTables;
    private final String schema;
    private final Map<String, SchemaInfo> schemaCache = new ConcurrentHashMap<>();

    public SchemaManager(DataSource dataSource, Text2SqlProperties properties) {
        this.dataSource = dataSource;
        this.allowedTables = new HashSet<>(properties.getAllowedTables());
        this.schema = properties.getSchema();
    }

    @PostConstruct
    public void initialize() {
        try (var conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();

            // 获取所有白名单表
            for (String tableName : allowedTables) {
                try {
                    // 获取列信息
                    List<SchemaInfo.ColumnInfo> columns = new ArrayList<>();
                    try (ResultSet rs = meta.getColumns(null, schema, tableName, null)) {
                        while (rs.next()) {
                            columns.add(new SchemaInfo.ColumnInfo(
                                    rs.getString("COLUMN_NAME"),
                                    rs.getString("TYPE_NAME"),
                                    "YES".equals(rs.getString("IS_NULLABLE")),
                                    rs.getString("COLUMN_DEF")
                            ));
                        }
                    }

                    // 获取主键
                    List<String> primaryKeys = new ArrayList<>();
                    try (ResultSet rs = meta.getPrimaryKeys(null, schema, tableName)) {
                        while (rs.next()) {
                            primaryKeys.add(rs.getString("COLUMN_NAME"));
                        }
                    }

                    // 获取外键
                    List<SchemaInfo.ForeignKeyInfo> foreignKeys = new ArrayList<>();
                    try (ResultSet rs = meta.getImportedKeys(null, schema, tableName)) {
                        while (rs.next()) {
                            foreignKeys.add(new SchemaInfo.ForeignKeyInfo(
                                    rs.getString("FKCOLUMN_NAME"),
                                    rs.getString("PKTABLE_NAME"),
                                    rs.getString("PKCOLUMN_NAME")
                            ));
                        }
                    }

                    SchemaInfo info = new SchemaInfo(tableName, columns, primaryKeys, foreignKeys);
                    schemaCache.put(tableName, info);
                    log.info("发现表 {}: {} 列, {} 主键, {} 外键",
                            tableName, columns.size(), primaryKeys.size(), foreignKeys.size());
                } catch (Exception e) {
                    log.warn("读取表 {} 结构失败: {}", tableName, e.getMessage());
                }
            }

            log.info("Schema 初始化完成：共发现 {} 张表", schemaCache.size());
        } catch (Exception e) {
            log.error("Schema 初始化失败", e);
        }
    }

    /**
     * 生成所有表的 DDL 描述文本（用于 prompt）。
     */
    public String getSchemaDdl() {
        StringBuilder sb = new StringBuilder();
        for (var entry : schemaCache.entrySet()) {
            SchemaInfo info = entry.getValue();
            sb.append("CREATE TABLE ").append(info.tableName()).append(" (\n");
            for (var col : info.columns()) {
                sb.append("  ").append(col.columnName()).append(" ").append(col.dataType());
                if (!col.nullable()) sb.append(" NOT NULL");
                if (col.defaultValue() != null) sb.append(" DEFAULT ").append(col.defaultValue());
                sb.append(",\n");
            }
            // 主键
            if (!info.primaryKeys().isEmpty()) {
                sb.append("  PRIMARY KEY (").append(String.join(", ", info.primaryKeys())).append("),\n");
            }
            // 外键
            for (var fk : info.foreignKeys()) {
                sb.append("  FOREIGN KEY (").append(fk.columnName())
                        .append(") REFERENCES ").append(fk.referencedTable())
                        .append("(").append(fk.referencedColumn()).append("),\n");
            }
            // 移除最后一个逗号
            int lastComma = sb.lastIndexOf(",");
            if (lastComma > 0) sb.deleteCharAt(lastComma);
            sb.append(");\n\n");
        }
        return sb.toString();
    }

    /**
     * 获取特定表的 schema 信息。
     */
    public SchemaInfo getTableSchema(String tableName) {
        return schemaCache.get(tableName);
    }

    /**
     * 验证表名是否在白名单中。
     */
    public boolean isTableAllowed(String tableName) {
        return allowedTables.contains(tableName.toLowerCase());
    }

    /**
     * 获取所有已缓存的表名。
     */
    public Set<String> getTableNames() {
        return Collections.unmodifiableSet(schemaCache.keySet());
    }
}