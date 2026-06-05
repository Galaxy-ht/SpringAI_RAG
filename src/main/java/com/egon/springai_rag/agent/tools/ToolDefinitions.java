package com.egon.springai_rag.agent.tools;

import com.egon.springai_rag.retrieval.HybridRetrievalService;
import com.egon.springai_rag.retrieval.ScoredDocument;
import lombok.extern.slf4j.Slf4j;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 工具定义工厂 — Agent 可用的所有工具。
 * <p>
 * 每个工具通过 {@link FunctionToolCallback} 创建，Spring AI 会自动将其
 * 转换为 OpenAI Function Calling 格式的 JSON Schema 发送给 LLM。
 */
@Slf4j
public class ToolDefinitions {

    private ToolDefinitions() {}

    public record RagSearchInput(String query) {}
    public record CalculatorInput(String expression) {}

    /**
     * RAG 知识库搜索工具 — 检索向量库中的相关文档。
     * <p>
     * 这是 Agent 与 P1-P4 检索管道的连接点。
     */
    // 【优化】移除未使用的 VectorStore 参数（ragSearchTool 仅依赖 HybridRetrievalService）
    public static ToolCallback ragSearchTool(HybridRetrievalService hybridRetrievalService) {
        return FunctionToolCallback
                .builder("rag_search", (RagSearchInput input) -> {
                    log.info("调用RAG搜索工具");
                    List<ScoredDocument> results = hybridRetrievalService.search(
                            input.query(), 5, "bm25", "rrf");
                    if (results.isEmpty()) {
                        return "未找到相关文档。";
                    }
                    return results.stream()
                            .map(doc -> "[" + doc.score() + "] " + doc.content())
                            .collect(Collectors.joining("\n---\n"));
                })
                .description("搜索 RAG 知识库中的相关文档。输入查询文本，返回相关文档内容和相关度分数。")
                .inputType(RagSearchInput.class)
                .build();
    }

    /**
     * 计算器工具 — 使用 exp4j 进行安全的数学表达式求值。
     */
    public static ToolCallback calculatorTool() {
        return FunctionToolCallback
                .builder("calculator", (CalculatorInput input) -> {
                    try {
                        log.info("调用计算工具");
                        double result = new ExpressionBuilder(input.expression())
                                .build()
                                .evaluate();
                        return String.valueOf(result);
                    } catch (Exception e) {
                        return "计算错误：" + e.getMessage();
                    }
                })
                .description("执行数学运算。输入数学表达式（如 '2+3*4'、'sqrt(16)'），返回计算结果。")
                .inputType(CalculatorInput.class)
                .build();
    }

    /**
     * 日期时间工具 — 获取当前时间。
     */
    public static ToolCallback dateTimeTool() {
        return FunctionToolCallback
                .builder("datetime", () -> {
                    log.info("调用时间获取工具");
                    return LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                })
                .description("获取当前日期和时间。无需输入参数。")
                .inputType(Void.class)
                .build();
    }
}