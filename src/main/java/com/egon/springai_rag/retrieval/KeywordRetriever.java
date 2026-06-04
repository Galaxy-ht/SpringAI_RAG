package com.egon.springai_rag.retrieval;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

/**
 * 关键词检索引擎抽象。不产生稠密向量，基于词频统计直接给文档打分。
 */
public interface KeywordRetriever {

    /**
     * @param documents 待索引的文档集合
     */
    AbstractRetriever.IndexedDocument index(List<Document> documents);

    /**
     * @param query 查询文本
     * @param topK  返回的文档数量
     * @return 按相关性降序排列的文档列表
     */
    List<ScoredDocument> search(String query, int topK);

    /**
     * @return 检索器名称，用于 API 选择
     */
    String getName();
}