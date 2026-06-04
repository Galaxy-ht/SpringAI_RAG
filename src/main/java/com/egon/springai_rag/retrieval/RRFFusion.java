package com.egon.springai_rag.retrieval;

import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Reciprocal Rank Fusion（倒数排名融合）。
 * <p>
 * 公式：RRF_score(d) = Σ 1 / (k + rank_i(d))
 * <p>
 * k 是平滑常数（默认 60），防止单个高排名结果主导融合。
 * rank_i(d) 是文档 d 在第 i 路检索结果中的排名（从 1 开始）。
 * <p>
 * 你需要实现的内容：
 * <ol>
 *   <li>将稠密结果按顺序分配排名（VectorStore 返回顺序即相似度降序，第一个 rank=1）</li>
 *   <li>将关键词结果按 score 降序分配排名</li>
 *   <li>对每个文档，计算 RRF 分数：两路排名分别代入公式后求和</li>
 *   <li>按 RRF 分数降序排序，返回 topK 个</li>
 * </ol>
 * <p>
 * 注意：同一个文档可能同时出现在稠密和关键词结果中，需要合并排名。
 */
@Component
public class RRFFusion implements FusionStrategy {

    @Value("${app.fusion.rrf.k:60}")
    private int k;

    @Override
    public List<ScoredDocument> fuse(List<Document> denseResults, List<ScoredDocument> sparseResults, int topK) {
        // 实现 RRF 融合
        // 1. 对稠密结果分配排名：第 i 个文档的 rank = i + 1
        // 2. 对关键词结果分配排名：按 score 降序，第 i 个文档的 rank = i + 1
        // 3. 用文档内容作为 key 去重合并：
        //    RRF_score(d) = 1/(k + dense_rank) + 1/(k + sparse_rank)
        //    如果文档只出现在一路结果中，只计算该路的贡献
        // 4. 按 RRF 分数降序排序，返回 topK 个 ScoredDocument


        // 使用内容字符串作为去重键（假设 text 能唯一标识文档）
        Map<String, Double> scoreMap = new HashMap<>();
        // 同时保存元数据，用于最后构造 ScoredDocument
        Map<String, Map<String, Object>> metadataMap = new HashMap<>();

        // 稠密结果
        for (int i = 0; i < denseResults.size(); i++) {
            double rrfScore = 1.0 / (k + (i + 1));
            Document doc = denseResults.get(i);
            String key = doc.getText();   // 内容作为唯一标识
            scoreMap.merge(key, rrfScore, Double::sum);
            metadataMap.putIfAbsent(key, doc.getMetadata());
        }

        // 稀疏结果：先按 score 降序排序
        List<ScoredDocument> sortedSparse = sparseResults.stream()
                .sorted(Comparator.comparing(ScoredDocument::score).reversed())
                .collect(Collectors.toList());

        for (int i = 0; i < sortedSparse.size(); i++) {
            double rrfScore = 1.0 / (k + (i + 1));
            ScoredDocument sd = sortedSparse.get(i);
            String key = sd.content();
            scoreMap.merge(key, rrfScore, Double::sum);
            metadataMap.putIfAbsent(key, sd.metadata());
        }

        // 转换为 ScoredDocument 列表并按总分降序排序
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> new ScoredDocument(entry.getKey(), entry.getValue(), metadataMap.get(entry.getKey())))
                .collect(Collectors.toList());
    }

    @Override
    public String getName() {
        return "rrf";
    }
}