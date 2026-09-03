package com.ithwx.personalknowledgebase.service;

import com.ithwx.personalknowledgebase.dto.RetrievedChunk;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class RagRetrievalService {

    private final VectorStore vectorStore;
    private final int topK;
    private final double similarityThreshold;

    public RagRetrievalService(
            VectorStore vectorStore,
            @Value("${app.rag.top-k:5}") int topK,
            @Value("${app.rag.similarity-threshold:0.55}") double similarityThreshold
    ) {
        if (topK <= 0) {
            throw new IllegalArgumentException("app.rag.top-k 必须大于 0");
        }
        if (Double.isNaN(similarityThreshold)
                || similarityThreshold < 0
                || similarityThreshold > 1) {
            throw new IllegalArgumentException(
                    "app.rag.similarity-threshold 必须在 0 到 1 之间"
            );
        }

        this.vectorStore = vectorStore;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    public List<RetrievedChunk> search(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("检索问题不能为空");
        }

        SearchRequest request = SearchRequest.builder()
                .query(question.strip())
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();

        List<Document> candidates = vectorStore.similaritySearch(request);
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<RetrievedChunk> results = new ArrayList<>();
        for (Document candidate : candidates) {
            if (!isRelevant(candidate)) {
                continue;
            }

            Map<String, Object> metadata = candidate.getMetadata();
            results.add(new RetrievedChunk(
                    candidate.getText(),
                    longMetadata(metadata, "documentId"),
                    stringMetadata(metadata, "documentName", "未知资料"),
                    stringMetadata(metadata, "sourceType", "unknown"),
                    nullableStringMetadata(metadata, "sourceUrl"),
                    intMetadata(metadata, "chunkIndex", -1),
                    candidate.getScore()
            ));
        }

        results.sort(
                Comparator.comparingDouble(RetrievedChunk::similarity).reversed()
        );
        return List.copyOf(results);
    }

    private boolean isRelevant(Document document) {
        return document != null
                && document.getText() != null
                && !document.getText().isBlank()
                && document.getScore() != null
                && document.getScore() >= similarityThreshold;
    }

    private String stringMetadata(
            Map<String, Object> metadata,
            String key,
            String defaultValue
    ) {
        Object value = metadata.get(key);
        if (value == null || value.toString().isBlank()) {
            return defaultValue;
        }
        return value.toString();
    }

    private String nullableStringMetadata(Map<String, Object> metadata, String key) {
        String value = stringMetadata(metadata, key, "");
        return value.isBlank() ? null : value;
    }

    private Long longMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer intMetadata(
            Map<String, Object> metadata,
            String key,
            int defaultValue
    ) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? defaultValue : Integer.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }
}
