package com.egon.springai_rag.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class DelegatingVectorStore implements VectorStore {

    private final Map<String, VectorStore> stores = new LinkedHashMap<>();
    private final AtomicReference<String> activeName = new AtomicReference<>();

    public DelegatingVectorStore(Map<String, VectorStore> stores, String initialActive) {
        this.stores.putAll(stores);
        this.activeName.set(normalize(initialActive));
    }

    public void setActiveStore(String storeName) {
        String name = normalize(storeName);
        if (!stores.containsKey(name)) {
            throw new IllegalArgumentException("未知向量库: " + storeName + "，可用: " + stores.keySet());
        }
        activeName.set(name);
    }

    public String getActiveStoreName() {
        return activeName.get();
    }

    public List<String> getAvailableStores() {
        return List.copyOf(stores.keySet());
    }

    private VectorStore active() {
        return stores.get(activeName.get());
    }

    private String normalize(String name) {
        for (String key : stores.keySet()) {
            if (key.equalsIgnoreCase(name) || key.equalsIgnoreCase(name + "store")) {
                return key;
            }
        }
        return name;
    }

    // ── VectorStore interface ──

    @Override
    public void add(List<Document> documents) {
        active().add(documents);
    }

    @Override
    public void accept(List<Document> documents) {
        active().accept(documents);
    }

    @Override
    public void delete(List<String> ids) {
        active().delete(ids);
    }

    @Override
    public void delete(Filter.Expression expression) {
        active().delete(expression);
    }

    @Override
    public void delete(String id) {
        active().delete(id);
    }

    @Override
    public String getName() {
        return "DelegatingVectorStore[" + activeName.get() + "]";
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        return active().similaritySearch(request);
    }

    @Override
    public List<Document> similaritySearch(String query) {
        return active().similaritySearch(query);
    }
}