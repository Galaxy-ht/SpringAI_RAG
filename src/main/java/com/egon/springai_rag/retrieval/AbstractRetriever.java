package com.egon.springai_rag.retrieval;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.document.Document;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractRetriever implements KeywordRetriever{
    protected final IndexedDocument indexedDocuments = new IndexedDocument();

    @Override
    public IndexedDocument index(List<Document> documents) {
        // 实现索引构建
        // 1. 遍历文档，对每个文档分词
        // 2. 构建倒排索引：词 → {文档ID: 词频}
        // 3. 计算每个文档的长度和平均长度
        // 4. 存储文档内容和元数据
        if (documents == null || documents.isEmpty()) {
            return indexedDocuments;
        }

        AtomicReference<Integer> length = new AtomicReference<>(0);
        Map<String, IndexedDocument.DocumentMetadata> documentsMetadata = new HashMap<>();

        documents.forEach(document -> {
            Map<String, Integer> words = splitText(document.getText());
            String documentId = document.getId();

            // 【优化】用总词频（含重复）作为文档长度，累加移到 forEach 外避免重复累加
            int docLength = words.values().stream().mapToInt(Integer::intValue).sum();
            length.updateAndGet(v -> v + docLength);

            IndexedDocument.DocumentMetadata documentMetadata = new IndexedDocument.DocumentMetadata(docLength, words, document);
            documentsMetadata.put(documentId, documentMetadata);

            Map<String, Set<String>> index = indexedDocuments.getIndex();
            Map<String, Integer> wordCount = indexedDocuments.getWordCount();

            words.forEach((key, value) -> {
                if (!index.containsKey(key)) {
                    HashSet<String> documentsId = new HashSet<>();
                    documentsId.add(documentId);
                    index.put(key, documentsId);
                    wordCount.put(key, value);
                } else {
                    index.get(key).add(documentId);
                    wordCount.put(key, wordCount.get(key) + value);
                }
            });
        });

        Double avgLength = length.get() / (double) documents.size();
        indexedDocuments.setAvgLength(avgLength);
        indexedDocuments.setDocCount(documents.size());
        indexedDocuments.setDocuments(documentsMetadata);
        return indexedDocuments;
    }

    // 添加你的数据结构（倒排索引、文档长度、平均长度等）
    @Data
    public static class IndexedDocument{
        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class DocumentMetadata {
            // 文档长度
            private Integer length;

            // 词频{word -> count}
            private Map<String, Integer> words = new HashMap<>();

            // 原始文档
            private Document document;
        }

        // {word -> id}
        private Map<String, Set<String>> index = new HashMap<>();

        // 文档数
        private Integer docCount;

        // 总词频{word -> wordCount}
        private Map<String, Integer> wordCount =  new HashMap<>();

        // 平均长度
        private Double avgLength;

        // 所有文档
        private Map<String, DocumentMetadata> documents = new HashMap<>();
    }


    // 分词器 — 中文按单字切分，英文按空格分词，过滤停用词
    public Map<String, Integer> splitText(String text) {
        Map<String, Integer> words = new HashMap<>();
        if (text == null || text.isEmpty()) return words;

        char[] charArray = text.toCharArray();
        StringBuilder tmp = new StringBuilder();
        for (char c : charArray) {
            if (isChinese(c)) {
                words.put(c + "", words.getOrDefault(c + "", 0) + 1);
            } else if (isEnglish(c)) {
                if (tmp.isEmpty()) {
                    tmp.append(c);
                } else if (isEnglish(tmp.charAt(tmp.length() - 1))) {
                    tmp.append(c);
                } else  {
                    words.put(tmp.toString(), words.getOrDefault(tmp.toString(), 0) + 1);
                    tmp = new StringBuilder();
                    tmp.append(c);
                }
            } else if (isNumber(c)) {
                if (tmp.isEmpty()) {
                    tmp.append(c);
                } else if (isNumber(tmp.charAt(tmp.length() - 1))) {
                    tmp.append(c);
                } else  {
                    words.put(tmp.toString(), words.getOrDefault(tmp.toString(), 0) + 1);
                    tmp = new StringBuilder();
                    tmp.append(c);
                }
            } else {
                if (!tmp.isEmpty()) {
                    words.put(tmp.toString(), words.getOrDefault(tmp.toString(), 0) + 1);
                    tmp = new StringBuilder();
                }
            }
        }
        if (!tmp.isEmpty()) {
            words.put(tmp.toString(), words.getOrDefault(tmp.toString(), 0) + 1);
        }
        return words;
    }

    private boolean isChinese(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    private boolean isEnglish(char c) {
        return ((((1 << Character.UPPERCASE_LETTER) |
                (1 << Character.LOWERCASE_LETTER)) >> Character.getType(c)) & 1)
                != 0;
    }

    private boolean isNumber(char c) {
        return ((((1 << Character.DECIMAL_DIGIT_NUMBER)) >> Character.getType(c)) & 1)
                != 0;
    }
}
