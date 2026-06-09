package com.egon.springai_rag.agent.text2sql;

import com.egon.springai_rag.agent.AbstractAgent;
import com.egon.springai_rag.agent.WorkerAgent;
import com.egon.springai_rag.agent.text2sql.dto.Text2SqlDtos;
import com.egon.springai_rag.agent.text2sql.dto.Text2SqlDtos.QueryHistoryRecord;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Text2SQL Agent — 带 Schema Linking、Few-shot 和结果解释的高级文本转 SQL 代理。
 */
@Slf4j
@Component("text2SqlAgent")
@WorkerAgent
public class Text2SqlAgent extends AbstractAgent {

    private static final int MAX_SQL_RETRIES = 2;

    private final SchemaManager schemaManager;
    private final SqlValidator sqlValidator;
    private final FewShotRetriever fewShotRetriever;
    private final Text2SqlTool text2SqlTool;
    private final Text2SqlProperties properties;
    private final ObjectMapper objectMapper;

    public Text2SqlAgent(ChatClient.Builder chatClientBuilder,
                         List<ToolCallback> tools,
                         SchemaManager schemaManager,
                         SqlValidator sqlValidator,
                         FewShotRetriever fewShotRetriever,
                         ObjectMapper objectMapper,
                         Text2SqlTool text2SqlTool,
                         Text2SqlProperties properties) {
        super(chatClientBuilder, tools, "Text2SQL-Agent");
        this.schemaManager = schemaManager;
        this.sqlValidator = sqlValidator;
        this.fewShotRetriever = fewShotRetriever;
        this.objectMapper = objectMapper;
        this.text2SqlTool = text2SqlTool;
        this.properties = properties;
    }

    @Override
    public String execute(String question) {
        String schemaDDL = schemaManager.getSchemaDdl();

        List<QueryHistoryRecord> similarExamples = fewShotRetriever.retrieveSimilarExamples(
                question, properties.getFewshot().getTopK());
        String fewShotPrompt = fewShotRetriever.buildFewShotPrompt(similarExamples);

        // SQL 生成 + 重试循环
        String sql = null;
        String lastError = null;
        for (int attempt = 0; attempt <= MAX_SQL_RETRIES; attempt++) {
            String prompt = buildNl2SqlPrompt(schemaDDL, fewShotPrompt, question,
                    attempt > 0 ? lastError : null);
            String response = chatClient.prompt().user(prompt).call().content();
            sql = text2SqlTool.extractSqlFromResponse(response);

            if (sql == null || sql.isBlank()) {
                lastError = "Failed to extract SQL from LLM response";
                log.warn("SQL extraction failed, attempt {}/{}", attempt + 1, MAX_SQL_RETRIES + 1);
                continue;
            }

            Text2SqlDtos.ValidationResult validation = sqlValidator.validate(sql);
            if (validation.isValid()) {
                break;
            }

            lastError = validation.errorMessage();
            log.warn("SQL validation failed (attempt {}): {}", attempt + 1, lastError);

            if (attempt == MAX_SQL_RETRIES) {
                return "SQL 校验失败（已重试 " + MAX_SQL_RETRIES + " 次）：" + lastError;
            }
        }

        if (sql == null || sql.isBlank()) {
            return "SQL 生成失败，请重试。";
        }

        // 执行查询
        long startTime = System.currentTimeMillis();
        List<Map<String, Object>> results = text2SqlTool.executeQuery(sql);
        long elapsedTime = System.currentTimeMillis() - startTime;

        // 回写成功的查询为 few-shot 示例
        fewShotRetriever.saveSuccessfulQuery(question, sql);

        // 结果格式化
        String formattedResults = text2SqlTool.formatResults(results, question, sql, elapsedTime);

        // 结果解释（LLM 自然语言摘要）
        String analysis = generateAnalysis(question, sql, results);

        return "**SQL：**\n```sql\n" + sql + "\n```\n\n" +
                "**结果：**\n" + formattedResults + "\n\n" +
                "**摘要：**\n" + analysis;
    }

    private String buildNl2SqlPrompt(String schemaDDL, String fewShotPrompt, String question,
                                     String previousError) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                You are an expert PostgreSQL developer. Convert the user's natural language
                question into a valid SQL SELECT query.

                Database Schema:
                """);
        prompt.append(schemaDDL).append("\n");

        if (fewShotPrompt != null && !fewShotPrompt.isEmpty()) {
            prompt.append(fewShotPrompt);
        }

        prompt.append("""
                Rules:
                1. ONLY write SELECT queries - never INSERT, UPDATE, DELETE, DROP, or TRUNCATE
                2. Use table aliases for clarity
                3. Join tables using proper foreign key relationships
                4. Always add LIMIT """).append(properties.getMaxRows())
                .append(" unless the user specifies otherwise\n")
                .append("5. Use meaningful column aliases for calculated fields\n")
                .append("6. Handle NULL values appropriately with COALESCE when needed\n")
                .append("7. Just return the SQL query without any additional text\n");

        if (previousError != null) {
            prompt.append("\nIMPORTANT: Your previous query was rejected with: ")
                    .append(previousError)
                    .append("\nPlease fix the issue and generate a valid query.\n");
        }

        prompt.append("\nQuestion: ").append(question).append("\n\n");
        prompt.append("Write the SQL query:");
        return prompt.toString();
    }

    private String generateAnalysis(String question, String sql, List<Map<String, Object>> results) {
        String jsonResult;
        try {
            jsonResult = objectMapper.writeValueAsString(results);
        } catch (Exception e) {
            log.warn("Failed to serialize results to JSON, using toString()", e);
            jsonResult = results.toString();
        }

        try {
            String analysisPrompt = """
                    You are a data analyst. Convert the following SQL query results into a
                    natural language summary.

                    Question: %s
                    SQL: %s
                    Results: %s

                    Provide a concise summary that:
                    1. States how many rows were returned
                    2. Highlights key findings
                    3. Answers the original question directly
                    """.formatted(question, sql, jsonResult);

            String analysis = chatClient.prompt().user(analysisPrompt).call().content();
            if (analysis != null && !analysis.isBlank()) {
                return analysis;
            }
        } catch (Exception e) {
            log.warn("LLM analysis generation failed, using fallback", e);
        }
        return "共返回 " + results.size() + " 条结果。";
    }
}