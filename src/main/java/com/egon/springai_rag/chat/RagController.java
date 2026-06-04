package com.egon.springai_rag.chat;

import com.egon.springai_rag.config.DelegatingVectorStore;
import com.egon.springai_rag.config.EmbeddingModelDelegator;
import com.egon.springai_rag.ingestion.IngestionService;
import com.egon.springai_rag.retrieval.KeywordRetriever;
import com.egon.springai_rag.retrieval.ScoredDocument;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RagController {

    private final RagService ragService;
    private final IngestionService ingestionService;
    private final DelegatingVectorStore delegatingVectorStore;
    private final EmbeddingModelDelegator embeddingModelDelegator;
    private final Map<String, KeywordRetriever> keywordRetrievers;

    public RagController(RagService ragService, IngestionService ingestionService,
                         DelegatingVectorStore delegatingVectorStore,
                         EmbeddingModelDelegator embeddingModelDelegator,
                         Map<String, KeywordRetriever> keywordRetrievers) {
        this.ragService = ragService;
        this.ingestionService = ingestionService;
        this.delegatingVectorStore = delegatingVectorStore;
        this.embeddingModelDelegator = embeddingModelDelegator;
        this.keywordRetrievers = keywordRetrievers;
    }

    @PostMapping("/ingest")
    public String ingest() {
        ingestionService.ingestSampleDocuments();
        return "示例文档已摄入到 " + delegatingVectorStore.getActiveStoreName();
    }

    @PostMapping("/query")
    public String query(@RequestBody String question) {
        return ragService.query(question);
    }

    @GetMapping("/vector-store")
    public Map<String, Object> vectorStoreInfo() {
        return Map.of(
                "active", delegatingVectorStore.getActiveStoreName(),
                "available", delegatingVectorStore.getAvailableStores()
        );
    }

    @PostMapping("/vector-store/switch")
    public Map<String, String> switchStore(@RequestParam String store) {
        delegatingVectorStore.setActiveStore(store);
        return Map.of(
                "message", "已切换到向量库：" + store,
                "active", delegatingVectorStore.getActiveStoreName()
        );
    }

    // ── 嵌入模型管理 ──

    @GetMapping("/embedding")
    public Map<String, Object> embeddingInfo() {
        return Map.of(
                "active", embeddingModelDelegator.getActiveModelName(),
                "available", embeddingModelDelegator.getAvailableModels(),
                "dimensions", embeddingModelDelegator.dimensions()
        );
    }

    @PostMapping("/embedding/switch")
    public Map<String, String> switchEmbedding(@RequestParam String model) {
        embeddingModelDelegator.setActiveModel(model);
        return Map.of(
                "message", "已切换到嵌入模型：" + model,
                "active", embeddingModelDelegator.getActiveModelName(),
                "dimensions", String.valueOf(embeddingModelDelegator.dimensions())
        );
    }

    // ── 关键词检索 ──

    @PostMapping("/keyword/search")
    public List<ScoredDocument> keywordSearch(
            @RequestParam(defaultValue = "bm25") String retriever,
            @RequestParam(defaultValue = "5") int topK,
            @RequestBody String query) {

        KeywordRetriever kr = keywordRetrievers.values().stream()
                .filter(r -> r.getName().equalsIgnoreCase(retriever))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "未知检索器: " + retriever + "，可用: " +
                        keywordRetrievers.values().stream().map(KeywordRetriever::getName).toList()));
        return kr.search(query, topK);
    }

    @PostMapping("/keyword/index")
    public String keywordIndex() {
        ingestionService.indexKeywordRetrievers();
        return "关键词索引已构建，可用检索器: " + keywordRetrievers.keySet();
    }
}