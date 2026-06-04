package com.egon.springai_rag.retrieval;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 融合策略：将多路检索结果合并为单一排序列表。
 */
public interface FusionStrategy {

    /**
     * @param denseResults  稠密检索结果（VectorStore 返回，按相似度降序）
     * @param sparseResults 关键词检索结果（按相关性分数降序）
     * @param topK          返回的文档数量
     * @return 融合后的排序结果
     */
    List<ScoredDocument> fuse(List<Document> denseResults, List<ScoredDocument> sparseResults, int topK);

    String getName();
}