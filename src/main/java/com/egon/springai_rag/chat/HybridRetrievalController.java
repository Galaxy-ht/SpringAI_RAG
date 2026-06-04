package com.egon.springai_rag.chat;

import com.egon.springai_rag.retrieval.HybridRetrievalService;
import com.egon.springai_rag.retrieval.ScoredDocument;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hybrid")
public class HybridRetrievalController {

    private final HybridRetrievalService hybridRetrievalService;
    private final ChatClient chatClient;

    public HybridRetrievalController(HybridRetrievalService hybridRetrievalService,
                                     ChatClient.Builder chatClientBuilder) {
        this.hybridRetrievalService = hybridRetrievalService;
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 混合检索（仅返回检索结果，不调用 LLM）。
     */
    @PostMapping("/search")
    public List<ScoredDocument> search(
            @RequestParam(defaultValue = "rrf") String fusion,
            @RequestParam(defaultValue = "bm25") String keyword,
            @RequestParam(defaultValue = "5") int topK,
            @RequestBody String query) {
        return hybridRetrievalService.search(query, topK, keyword, fusion);
    }

    /**
     * 端到端混合 RAG：混合检索 + LLM 生成回答。
     */
    @PostMapping("/rag")
    public String rag(
            @RequestParam(defaultValue = "rrf") String fusion,
            @RequestParam(defaultValue = "bm25") String keyword,
            @RequestParam(defaultValue = "5") int topK,
            @RequestBody String query) {

        List<ScoredDocument> results = hybridRetrievalService.search(query, topK, keyword, fusion);

        if (results.isEmpty()) {
            return "未找到相关文档。";
        }

        // 将融合结果拼接为上下文，复用 RagService 的 LLM 调用
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            context.append("[").append(i + 1).append("] ").append(results.get(i).content()).append("\n");
        }
        String prompt = "根据以下文档回答问题。如果文档中没有相关信息，请如实说明。\n\n文档：\n" + context + "\n问题：" + query;
        return chatClient.prompt().user(prompt).call().content();
    }

    @PostMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "keywordRetrievers", hybridRetrievalService.getAvailableKeywordRetrievers(),
                "fusionStrategies", hybridRetrievalService.getAvailableFusionStrategies()
        );
    }
}