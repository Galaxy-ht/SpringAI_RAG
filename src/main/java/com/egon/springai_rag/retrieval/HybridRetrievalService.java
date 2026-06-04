package com.egon.springai_rag.retrieval;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class HybridRetrievalService {

    private final VectorStore vectorStore;
    private final Map<String, KeywordRetriever> keywordRetrievers;
    private final Map<String, FusionStrategy> fusionStrategies;

    public HybridRetrievalService(VectorStore vectorStore,
                                  Map<String, KeywordRetriever> keywordRetrievers,
                                  Map<String, FusionStrategy> fusionStrategies) {
        this.vectorStore = vectorStore;
        this.keywordRetrievers = keywordRetrievers;
        this.fusionStrategies = fusionStrategies;
    }

    /**
     * 执行混合检索：稠密 + 关键词 → 融合。
     *
     * @param query          查询文本
     * @param topK           返回数量
     * @param keywordRetriever 关键词检索器名称（bm25 / tfidf）
     * @param fusionStrategy 融合策略名称（rrf / weighted）
     */
    public List<ScoredDocument> search(String query, int topK, String keywordRetriever, String fusionStrategy) {
        // 稠密检索
        List<Document> denseResults = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build());

        // 关键词检索
        KeywordRetriever kr = keywordRetrievers.values().stream()
                .filter(r -> r.getName().equalsIgnoreCase(keywordRetriever))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "未知检索器: " + keywordRetriever + "，可用: " +
                        keywordRetrievers.values().stream().map(KeywordRetriever::getName).toList()));

        List<ScoredDocument> sparseResults = kr.search(query, topK);

        // 融合
        FusionStrategy fusion = fusionStrategies.values().stream()
                .filter(f -> f.getName().equalsIgnoreCase(fusionStrategy))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "未知融合策略: " + fusionStrategy + "，可用: " +
                        fusionStrategies.values().stream().map(FusionStrategy::getName).toList()));

        return fusion.fuse(denseResults, sparseResults, topK);
    }

    public List<String> getAvailableKeywordRetrievers() {
        return keywordRetrievers.values().stream().map(KeywordRetriever::getName).toList();
    }

    public List<String> getAvailableFusionStrategies() {
        return fusionStrategies.values().stream().map(FusionStrategy::getName).toList();
    }
}