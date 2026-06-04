package com.egon.springai_rag.ingestion;

import com.egon.springai_rag.retrieval.KeywordRetriever;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class IngestionService {

    private final VectorStore vectorStore;
    private final Map<String, KeywordRetriever> keywordRetrievers;

    public IngestionService(VectorStore vectorStore, Map<String, KeywordRetriever> keywordRetrievers) {
        this.vectorStore = vectorStore;
        this.keywordRetrievers = keywordRetrievers;
    }

    private List<Document> sampleDocuments() {
        return List.of(
                new Document("Spring AI 是 Spring 生态的 AI 集成框架，提供统一的模型调用、向量存储和 Agent 抽象。",
                        Map.of("source", "spring-ai-intro", "category", "framework")),
                new Document("RAG（检索增强生成）通过从向量库检索相关文档，将其作为上下文注入 LLM 提示，减少幻觉并提升回答准确性。",
                        Map.of("source", "rag-concept", "category", "technique")),
                new Document("pgvector 是 PostgreSQL 的向量扩展，支持 HNSW 和 IVFFlat 索引，适合中小规模向量检索场景。",
                        Map.of("source", "pgvector-intro", "category", "vector-store")),
                new Document("混合检索融合（Hybrid Retrieval Fusion）将多路检索结果合并排序，常见算法有 RRF（Reciprocal Rank Fusion）和加权合并。",
                        Map.of("source", "hybrid-retrieval", "category", "technique")),
                new Document("Agentic RAG 让 LLM 自主决定何时检索、检索什么、如何组合结果，相比静态 RAG 更灵活但更复杂。",
                        Map.of("source", "agentic-rag", "category", "technique")),
                new Document("HNSW 索引基于层级可导航小世界图，查询速度快但构建慢；IVFFlat 先聚类再搜索，适合大规模数据但精度略低。",
                        Map.of("source", "index-types", "category", "vector-store"))
        );
    }

    public void ingestSampleDocuments() {
        vectorStore.add(sampleDocuments());
    }

    public void indexKeywordRetrievers() {
        List<Document> docs = sampleDocuments();
        keywordRetrievers.values().forEach(retriever -> retriever.index(docs));
    }
}