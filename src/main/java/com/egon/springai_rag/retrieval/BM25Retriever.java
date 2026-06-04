package com.egon.springai_rag.retrieval;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * BM25 关键词检索器。
 * <p>
 * 你需要实现的内容：
 * <ol>
 *   <li><b>分词器</b> — 中文按单字切分，英文按空格分词，过滤停用词</li>
 *   <li><b>倒排索引</b> — 词 → 文档ID列表 + 词频</li>
 *   <li><b>BM25 公式</b> — score(q,d) = Σ IDF(qi) * (fi*(k1+1)) / (fi + k1*(1-b + b*dl/avgdl))</li>
 * </ol>
 * <p>
 * 参数：k1=1.5（词频饱和），b=0.75（文档长度归一化）
 */
@Component("bm25")
public class BM25Retriever extends AbstractRetriever implements KeywordRetriever {

    // 【优化】k1=1.5（词频饱和参数），与 BM25 文献命名一致
    private static final double k1 = 1.5;
    private static final double b = 0.75;
    private boolean indexed = false;

    @Override
    public IndexedDocument index(List<Document> documents) {
        indexed = true;
        return super.index(documents);
    }

    @Override
    public List<ScoredDocument> search(String query, int topK) {
        if (!indexed || StringUtils.isEmpty(query)) {
            return List.of();
        }

        // 实现 BM25 检索
        // 1. 对查询分词
        // 2. 对每个查询词，从倒排索引中获取文档列表
        // 3. 计算 BM25 分数: score = IDF(qi) * ((fi * (K1+1)) / (fi + K1 * (1-B + B * dl/avgdl)))
        //    - IDF(qi) = log((N - n(qi) + 0.5) / (n(qi) + 0.5) + 1)
        //    - fi = 词在文档中的频率
        //    - dl = 文档长度
        //    - avgdl = 平均文档长度
        //    - N = 文档总数
        //    - n(qi) = 包含词 qi 的文档数
        // 4. 按分数降序排序，返回 topK 个 ScoredDocument


        List<ScoredDocument> result = new ArrayList<>();
        // 所有文档
        Map<String, IndexedDocument.DocumentMetadata> documents = indexedDocuments.getDocuments();
        Set<String> queryWords = splitText(query).keySet();

        documents.forEach((docId, metadata) -> {

            // 【优化】用 double[] 替代 AtomicReference，减少装箱开销
            double[] score = {0.0};
            int N = indexedDocuments.getDocCount();
            double lengthWeight = metadata.getLength() / indexedDocuments.getAvgLength();
            double K = k1 * (1 - b + b * lengthWeight);

            queryWords.forEach(queryWord -> {

                int n = 0;
                if (indexedDocuments.getIndex().get(queryWord) != null) {
                    n = indexedDocuments.getIndex().get(queryWord).size();
                }
                int fi = 0;
                if (metadata.getWords().get(queryWord) != null) {
                    fi = metadata.getWords().get(queryWord);
                }
                double idf = IDF(N, n);
                // 【优化】移除冗余 Math.abs()——IDF 和项贡献均不会为负
                double score_i = idf * (((k1 + 1) * fi) / (fi + K));
                score[0] += score_i;
            });

            result.add(new ScoredDocument(indexedDocuments.getDocuments().get(docId).getDocument().getText(), score[0], metadata.getDocument().getMetadata()));
        });

        return result.stream().sorted(Comparator.comparing(ScoredDocument::score).reversed()).limit(topK).collect(Collectors.toList());
    }

    // 【优化】移除冗余 Math.abs()——IDF 公式保证结果为非负
    private double IDF(int N, int n) {
        return Math.log(((N - n + 0.5) / (n + 0.5)) + 1);
    }

    @Override
    public String getName() {
        return "bm25";
    }
}