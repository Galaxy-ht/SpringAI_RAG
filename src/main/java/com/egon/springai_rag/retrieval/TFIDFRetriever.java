package com.egon.springai_rag.retrieval;

import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TF-IDF 关键词检索器（经典基线）。
 * <p>
 * 你需要实现的内容：
 * <ol>
 *   <li><b>分词器</b> — 与 BM25 共用相同的分词逻辑</li>
 *   <li><b>TF-IDF 公式</b> — score(t,d) = tf(t,d) * log(N / df(t))</li>
 * </ol>
 * <p>
 * 建议：先从 BM25 提取分词和索引构建逻辑到一个共享的 Tokenizer 工具类，再在两个检索器中复用。
 */
@Component("tfidf")
public class TFIDFRetriever extends AbstractRetriever implements KeywordRetriever {

    private boolean indexed = false;

    @Override
    public IndexedDocument index(List<Document> documents) {
        indexed = true;
        return super.index(documents);
    }

    @Override
    public List<ScoredDocument> search(String query, int topK) {
        // 【优化】增加空查询守卫，与 BM25Retriever 保持一致
        if (!indexed || StringUtils.isEmpty(query)) {
            return List.of();
        }

        // 实现 TF-IDF 检索
        // 1. 对查询分词
        // 2. 对每个查询词，计算 TF-IDF 分数
        //    - TF(t,d) = 词 t 在文档 d 中的频率
        //    - IDF(t) = log(N / df(t))，其中 N=总文档数，df(t)=包含词 t 的文档数
        //    - score = Σ TF(t,d) * IDF(t) 对所有查询词求和
        // 3. 按分数降序排序，返回 topK 个 ScoredDocument
        List<ScoredDocument> result = new ArrayList<>();
        Map<String, IndexedDocument.DocumentMetadata> documents = indexedDocuments.getDocuments();
        Set<String> queryWords = splitText(query).keySet();

        documents.forEach((docId, metadata) -> {
            // 【优化】用 double[] 替代 AtomicReference，减少装箱开销
            double[] score = {0.0};
            int N = indexedDocuments.getDocCount();

            queryWords.forEach(queryWord -> {
                int n = 0;
                if (indexedDocuments.getIndex().get(queryWord) != null) {
                    n = indexedDocuments.getIndex().get(queryWord).size();
                }

                int TF = 0;
                if (metadata.getWords().get(queryWord) != null) {
                    TF = metadata.getWords().get(queryWord);
                }

                double IDF = n != 0 ? Math.log(N / (double) n) : 0;
                double score_i = TF * IDF;
                score[0] += score_i;
            });
            result.add(new ScoredDocument(indexedDocuments.getDocuments().get(docId).getDocument().getText(), score[0], metadata.getDocument().getMetadata()));
        });

        return result.stream().sorted(Comparator.comparing(ScoredDocument::score).reversed()).limit(topK).collect(Collectors.toList());
    }

    @Override
    public String getName() {
        return "tfidf";
    }
}