package com.egon.springai_rag.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class EmbeddingModelDelegator implements EmbeddingModel {

    private final Map<String, EmbeddingModel> models = new LinkedHashMap<>();
    private final AtomicReference<String> activeName = new AtomicReference<>();

    public EmbeddingModelDelegator(Map<String, EmbeddingModel> models, String initialActive) {
        this.models.putAll(models);
        this.activeName.set(initialActive);
    }

    public void setActiveModel(String modelName) {
        if (!models.containsKey(modelName)) {
            throw new IllegalArgumentException("未知嵌入模型: " + modelName + "，可用: " + models.keySet());
        }
        activeName.set(modelName);
    }

    public String getActiveModelName() {
        return activeName.get();
    }

    public List<String> getAvailableModels() {
        return List.copyOf(models.keySet());
    }

    private EmbeddingModel active() {
        return models.get(activeName.get());
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return active().call(request);
    }

    @Override
    public float[] embed(Document document) {
        return active().embed(document);
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return active().embed(texts);
    }

    @Override
    public float[] embed(String text) {
        return active().embed(text);
    }

    @Override
    public int dimensions() {
        return active().dimensions();
    }
}