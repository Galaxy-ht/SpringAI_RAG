package com.egon.springai_rag.agent.text2sql;

import com.egon.springai_rag.agent.text2sql.dto.Text2SqlDtos.QueryHistoryRecord;
import com.egon.springai_rag.retrieval.HybridRetrievalService;
import com.egon.springai_rag.retrieval.ScoredDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Few-shot 检索器 — 使用 RAG 检索历史 NL→SQL 查询对，并支持将成功查询回写向量库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FewShotRetriever {

    private final HybridRetrievalService hybridRetrievalService;
    private final VectorStore vectorStore;

    /**
     * 检索相似的历史查询示例。
     */
    public List<QueryHistoryRecord> retrieveSimilarExamples(String question, int topK) {
        List<ScoredDocument> search = hybridRetrievalService.search(question, topK, "bm25", "rrf");
        if (search.isEmpty()) {
            return Collections.emptyList();
        }

        List<QueryHistoryRecord> records = new ArrayList<>();
        for (var doc : search) {
            Map<String, Object> metadata = doc.metadata();
            if (metadata == null) continue;
            Object sql = metadata.get("sql");
            if (sql == null) continue;
            records.add(new QueryHistoryRecord(
                    doc.content(),          // 历史问题（从向量库检索到的文档内容）
                    sql.toString(),         // 历史 SQL（从 metadata 取出）
                    null,                   // resultSummary 未存储
                    doc.score()             // 相似度分数
            ));
        }
        return records;
    }

    /**
     * 构建 Few-shot prompt 文本。
     */
    public String buildFewShotPrompt(List<QueryHistoryRecord> examples) {
        if (examples.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("Here are some example queries:\n\n");
        for (int i = 0; i < examples.size(); i++) {
            var ex = examples.get(i);
            sb.append("Example ").append(i + 1).append(":\n");
            sb.append("Q: ").append(ex.question()).append("\n");
            sb.append("SQL: ").append(ex.sql()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 将成功的查询回写到向量库，供后续 Few-shot 检索使用。
     */
    public void saveSuccessfulQuery(String question, String sql) {
        try {
            var doc = new Document(question, Map.of("sql", sql, "category", "fewshot"));
            vectorStore.add(List.of(doc));
            log.info("Few-shot example saved: {}", question);
        } catch (Exception e) {
            log.warn("Failed to save few-shot example: {}", e.getMessage());
        }
    }
}