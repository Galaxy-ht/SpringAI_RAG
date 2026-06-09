package com.egon.springai_rag.agent.text2sql;

import com.egon.springai_rag.agent.text2sql.dto.Text2SqlDtos.QueryHistoryRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * text2SQL 工具 — 将自然语言转换为 SQL 并执行。
 * <p>
 * 作为 ToolCallback 可被任何 Agent（ReAct、Reflection 等）通过 Tool Catalog 调用。
 */
@Slf4j
@Component
public class Text2SqlTool {

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final SchemaManager schemaManager;
    private final SqlValidator sqlValidator;
    private final FewShotRetriever fewShotRetriever;
    private final JdbcTemplate jdbcTemplate;
    private final Text2SqlProperties properties;

    public Text2SqlTool(ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
                        SchemaManager schemaManager,
                        SqlValidator sqlValidator,
                        FewShotRetriever fewShotRetriever,
                        JdbcTemplate jdbcTemplate,
                        Text2SqlProperties properties) {
        this.chatClientBuilderProvider = chatClientBuilderProvider;
        this.schemaManager = schemaManager;
        this.sqlValidator = sqlValidator;
        this.fewShotRetriever = fewShotRetriever;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public ToolCallback createToolCallback() {
        return FunctionToolCallback
                .builder("text2sql", (Text2SqlInput input) -> {
                    log.info("text2SQL 工具调用：{}", input.question());

                    String schema = schemaManager.getSchemaDdl();
                    List<QueryHistoryRecord> fewShots = fewShotRetriever.retrieveSimilarExamples(
                            input.question(), properties.getFewshot().getTopK());
                    String fewShotPrompt = fewShots.isEmpty() ? "" :
                            fewShotRetriever.buildFewShotPrompt(fewShots);

                    String sql = generateSql(input.question(), schema, fewShotPrompt);
                    if (sql == null || sql.isBlank()) {
                        return "SQL 生成失败，请重试。";
                    }

                    var validation = sqlValidator.validate(sql);
                    if (!validation.isValid()) {
                        return "SQL 校验失败：" + validation.errorMessage();
                    }

                    long start = System.currentTimeMillis();
                    List<Map<String, Object>> results = executeQuery(sql);
                    long elapsed = System.currentTimeMillis() - start;

                    // 将成功查询回写为 few-shot 示例
                    fewShotRetriever.saveSuccessfulQuery(input.question(), sql);

                    return formatResults(results, input.question(), sql, elapsed);
                })
                .description("将自然语言问题转换为 SQL 查询并执行。输入问题描述，返回数据库查询结果。")
                .inputType(Text2SqlInput.class)
                .build();
    }

    public record Text2SqlInput(String question) {}

    public String generateSql(String question, String schema, String fewShotPrompt) {
        ChatClient chatClient = chatClientBuilderProvider.getObject().build();

        String prompt = """
                You are an expert PostgreSQL developer. Convert the user's natural language
                question into a valid SQL SELECT query.

                Database Schema:
                {schema_ddl}

                {few_shot_examples_section}

                Rules:
                1. ONLY write SELECT queries - never INSERT, UPDATE, DELETE, DROP, or TRUNCATE
                2. Use table aliases for clarity
                3. Join tables using proper foreign key relationships
                4. Always add LIMIT {max_rows} unless the user specifies otherwise
                5. Use meaningful column aliases for calculated fields
                6. Handle NULL values appropriately with COALESCE when needed
                7. Just return the SQL query, do not include any additional text

                Question: {question}

                Write the SQL query:
                """
                .replace("{schema_ddl}", schema)
                .replace("{few_shot_examples_section}", fewShotPrompt)
                .replace("{max_rows}", String.valueOf(properties.getMaxRows()))
                .replace("{question}", question);

        log.info("LLM Prompt:\n{}", prompt);
        String content = chatClient.prompt().user(prompt).call().content();

        if (content == null || content.isBlank()) {
            log.error("LLM 响应为空");
            return null;
        }

        return extractSqlFromResponse(content.trim());
    }

    String extractSqlFromResponse(String response) {
        if (response.startsWith("SELECT") || response.startsWith("WITH")) {
            return cleanSql(response);
        }
        // 处理 ```sql ... ``` 或 ``` ... ```
        int fenceStart = response.indexOf("```");
        if (fenceStart >= 0) {
            int contentStart = response.indexOf('\n', fenceStart);
            if (contentStart < 0) contentStart = fenceStart + 3;
            else contentStart += 1;
            int fenceEnd = response.indexOf("```", contentStart);
            if (fenceEnd < 0) fenceEnd = response.length();
            return cleanSql(response.substring(contentStart, fenceEnd).trim());
        }
        return cleanSql(response);
    }

    private String cleanSql(String sql) {
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1);
        }
        return sql.trim();
    }

    public List<Map<String, Object>> executeQuery(String sql) {
        return jdbcTemplate.queryForList(sql);
    }

    public String formatResults(List<Map<String, Object>> results, String question,
                                String sql, long elapsedMs) {
        if (CollectionUtils.isEmpty(results)) {
            return "未找到匹配数据。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**问题：** ").append(question).append("\n\n");
        sb.append("**SQL：** `").append(sql).append("`\n\n");
        sb.append("**耗时：** ").append(elapsedMs).append("ms\n\n");

        if (results.size() <= 10) {
            sb.append(buildMarkdownTable(results, results.size()));
        } else {
            sb.append("共 ").append(results.size()).append(" 行，显示前 10 行：\n\n");
            sb.append(buildMarkdownTable(results, 10));
        }
        return sb.toString();
    }

    private String buildMarkdownTable(List<Map<String, Object>> results, int limit) {
        if (results.isEmpty()) return "";

        List<String> columns = new ArrayList<>(results.get(0).keySet());

        StringBuilder sb = new StringBuilder();
        // Header
        sb.append("| ").append(String.join(" | ", columns)).append(" |\n");
        // Separator
        sb.append("| ").append(columns.stream().map(c -> "---").collect(Collectors.joining(" | "))).append(" |\n");
        // Data rows
        for (int i = 0; i < limit && i < results.size(); i++) {
            Map<String, Object> row = results.get(i);
            sb.append("| ");
            sb.append(columns.stream()
                    .map(col -> Objects.toString(row.get(col), "NULL"))
                    .collect(Collectors.joining(" | ")));
            sb.append(" |\n");
        }
        return sb.toString();
    }
}