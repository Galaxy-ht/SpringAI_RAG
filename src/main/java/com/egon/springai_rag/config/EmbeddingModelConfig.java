package com.egon.springai_rag.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class EmbeddingModelConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingModelConfig.class);

    @Value("${app.embedding.active:dashscope}")
    private String activeModel;

    // ── DashScope (text-embedding-v2, 1536 dims) ──

    @Bean("dashscopeEmbeddingModel")
    @Lazy
    public EmbeddingModel dashscopeEmbeddingModel(
            @Value("${spring.ai.openai.embedding.api-key:${DASHSCOPE_API_KEY}}") String apiKey,
            @Value("${spring.ai.openai.embedding.base-url:${DASHSCOPE_BASE_URL}}") String baseUrl,
            @Value("${spring.ai.openai.embedding.model:text-embedding-v2}") String model) {

        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(model)
                .build();

        return new OpenAiEmbeddingModel(client, MetadataMode.EMBED, options);
    }

    // ── BGE-M3 via SiliconFlow (1024 dims) ──

    @Bean("bgeM3EmbeddingModel")
    @Lazy
    public EmbeddingModel bgeM3EmbeddingModel(
            @Value("${spring.ai.siliconflow.api-key}") String apiKey,
            @Value("${spring.ai.siliconflow.base-url}") String baseUrl,
            @Value("${spring.ai.siliconflow.embedding.model}") String model) {

        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(model)
                .build();

        return new OpenAiEmbeddingModel(client, MetadataMode.EMBED, options);
    }

    // ── EmbeddingModelDelegator (@Primary) ──

    @Bean
    @Primary
    public EmbeddingModelDelegator embeddingModelDelegator(
            ApplicationContext context,
            @Value("${app.embedding.active:dashscope}") String active) {

        Map<String, EmbeddingModel> models = new LinkedHashMap<>();

        if (context.containsBean("dashscopeEmbeddingModel")) {
            EmbeddingModel m = tryGetModel(context, "dashscopeEmbeddingModel");
            if (m != null) models.put("dashscope", m);
        }
        if (context.containsBean("bgeM3EmbeddingModel")) {
            EmbeddingModel m = tryGetModel(context, "bgeM3EmbeddingModel");
            if (m != null) models.put("bge-m3", m);
        }

        if (models.isEmpty()) {
            throw new IllegalStateException("No embedding model available. Please check your configuration.");
        }

        String resolvedActive = models.containsKey(active) ? active : models.keySet().iterator().next();
        if (!resolvedActive.equals(active)) {
            log.warn("配置的活跃嵌入模型 '{}' 不可用，已回退到 '{}'", active, resolvedActive);
        }

        return new EmbeddingModelDelegator(models, resolvedActive);
    }

    private EmbeddingModel tryGetModel(ApplicationContext context, String beanName) {
        try {
            return context.getBean(beanName, EmbeddingModel.class);
        } catch (Exception e) {
            log.warn("嵌入模型 {} 不可用，已跳过: {}", beanName, e.getMessage());
            return null;
        }
    }
}