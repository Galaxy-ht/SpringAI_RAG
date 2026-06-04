package com.egon.springai_rag.retrieval;

import java.util.Map;

public record ScoredDocument(String content, double score, Map<String, Object> metadata) {
}