package com.egon.springai_rag.agent.text2sql;

import com.egon.springai_rag.agent.text2sql.dto.Text2SqlDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Text2SQL REST 控制器。
 *
 * <h3>端点说明</h3>
 * <ul>
 *   <li>GET  /api/text2sql/schema — 查看当前数据库 Schema</li>
 *   <li>POST /api/text2sql/query — 执行自然语言查询（委托给 Text2SqlAgent）</li>
 *   <li>GET  /api/text2sql/history — 查看历史查询记录</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/text2sql")
@RequiredArgsConstructor
public class Text2SqlController {

    private final Text2SqlAgent text2SqlAgent;
    private final SchemaManager schemaManager;
    private final Text2SqlProperties properties;

    @GetMapping("/schema")
    public Map<String, Object> getSchema() {
        List<Map<String, Object>> tables = new ArrayList<>();
        for (String tableName : schemaManager.getTableNames()) {
            var info = schemaManager.getTableSchema(tableName);
            if (info != null) {
                tables.add(Map.of(
                        "table", info.tableName(),
                        "columns", info.columns().stream()
                                .map(c -> Map.of(
                                        "name", c.columnName(),
                                        "type", c.dataType(),
                                        "nullable", c.nullable()
                                )).toList(),
                        "primaryKeys", info.primaryKeys(),
                        "foreignKeys", info.foreignKeys().stream()
                                .map(fk -> Map.of(
                                        "column", fk.columnName(),
                                        "references", fk.referencedTable() + "(" + fk.referencedColumn() + ")"
                                )).toList()
                ));
            }
        }
        return Map.of(
                "tableCount", tables.size(),
                "allowedTables", properties.getAllowedTables(),
                "tables", tables
        );
    }

    /**
     * 执行自然语言查询 — 委托给 Text2SqlAgent 执行完整 pipeline。
     */
    @PostMapping("/query")
    public Map<String, Object> query(@RequestBody Text2SqlDtos.Text2SqlRequest request) {
        log.info("Text2SQL 查询请求: {}", request.question());
        String result = text2SqlAgent.execute(request.question());
        return Map.of("question", request.question(), "result", result);
    }

    /**
     * 查看历史查询记录。
     */
    @GetMapping("/history")
    public Map<String, Object> getHistory(@RequestParam(defaultValue = "10") int limit) {
        return Map.of(
                "message", "历史查询数据存储在向量库中。成功的查询会自动保存为 few-shot 示例。",
                "note", "可以使用 GET /api/text2sql/schema 查看数据库结构"
        );
    }
}