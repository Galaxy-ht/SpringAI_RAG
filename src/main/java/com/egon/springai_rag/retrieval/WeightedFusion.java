package com.egon.springai_rag.retrieval;

import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 加权分数融合。
 * <p>
 * 公式：final_score(d) = α * norm_dense(d) + (1-α) * norm_sparse(d)
 * <p>
 * 你需要实现的内容：
 * <ol>
 *   <li>对稠密相似度做 min-max 归一化到 [0,1] 区间</li>
 *   <li>对关键词 score 做 min-max 归一化到 [0,1] 区间</li>
 *   <li>用文档内容作为 key 去重合并，加权求和</li>
 *   <li>按融合分数降序排序，返回 topK 个</li>
 * </ol>
 * <p>
 * 注意：VectorStore 返回的 Document 没有 score 字段。可以用 "1.0 / (index + 1)" 作为相似度的近似值，
 * 或者使用 distance 元数据（如果可用）。
 */
@Component
public class WeightedFusion implements FusionStrategy {

    @Value("${app.fusion.weighted.alpha:0.5}")
    private double alpha;

    @Override
    public List<ScoredDocument> fuse(List<Document> denseResults, List<ScoredDocument> sparseResults, int topK) {
        // 实现加权融合
        // 1. 从稠密结果中提取分数（无 score 时用 1.0/(index+1) 近似）
        // 2. 对稠密分数做 min-max 归一化：norm_score = (score - min) / (max - min)
        // 3. 对关键词分数做 min-max 归一化
        // 4. 去重合并：final_score = α * norm_dense + (1-α) * norm_sparse
        //    如果文档只出现在一路结果中，只使用该路的归一化分数
        // 5. 按 final_score 降序排序，返回 topK 个

        // 1. 为稠密结果生成分数：按排名递减（第1名分数最高）
        List<Double> denseScores = new ArrayList<>();
        for (int i = 0; i < denseResults.size(); i++) {
            double score = 1.0 / (i + 1);   // 排名第 i+1 的分数
            denseScores.add(score);
        }

        // 2. 提取稀疏结果的分数（直接用 ScoredDocument 的 score 方法）
        List<Double> sparseScores = sparseResults.stream()
                .map(ScoredDocument::score)
                .collect(Collectors.toList());

        // 3. min-max 归一化
        List<Double> normDense = minMaxNormalize(denseScores);
        List<Double> normSparse = minMaxNormalize(sparseScores);

        // 4. 按文档内容（text）合并，计算最终分数
        Map<String, Double> finalScoreMap = new HashMap<>();
        Map<String, Map<String, Object>> metadataMap = new HashMap<>();

        // 处理稠密结果
        for (int i = 0; i < denseResults.size(); i++) {
            Document doc = denseResults.get(i);
            String key = doc.getText();          // 获取文档内容作为唯一标识
            double weightedScore = alpha * normDense.get(i);
            finalScoreMap.merge(key, weightedScore, Double::sum);
            metadataMap.putIfAbsent(key, doc.getMetadata());
        }

        // 处理稀疏结果
        for (int i = 0; i < sparseResults.size(); i++) {
            ScoredDocument sd = sparseResults.get(i);
            String key = sd.content();            // ScoredDocument 的内容
            double weightedScore = (1 - alpha) * normSparse.get(i);
            finalScoreMap.merge(key, weightedScore, Double::sum);
            metadataMap.putIfAbsent(key, sd.metadata());
        }

        // 5. 按最终分数降序排序，取 topK，构造 ScoredDocument 返回
        return finalScoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> new ScoredDocument(
                        entry.getKey(),
                        entry.getValue(),
                        metadataMap.get(entry.getKey())))
                .collect(Collectors.toList());
    }

    private List<Double> minMaxNormalize(List<Double> scores) {
        if (scores.isEmpty()) return Collections.emptyList();
        double min = Collections.min(scores);
        double max = Collections.max(scores);
        if (Math.abs(max - min) < 1e-8) {
            return scores.stream().map(s -> 0.5).collect(Collectors.toList());
        }
        return scores.stream().map(s -> (s - min) / (max - min)).collect(Collectors.toList());
    }

    @Override
    public String getName() {
        return "weighted";
    }
}