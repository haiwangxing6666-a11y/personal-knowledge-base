package com.ithwx.personalknowledgebase.service;

import com.ithwx.personalknowledgebase.dto.RetrievedChunk;
import com.ithwx.personalknowledgebase.dto.TwoStageRetrievalResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TwoStageRetrievalService {

    private final RagRetrievalService retrievalService;
    private final QuestionRewriteService questionRewriteService;
    private final int retryMinHits;
    private final int maxResults;

    public TwoStageRetrievalService(
            RagRetrievalService retrievalService,
            QuestionRewriteService questionRewriteService,
            @Value("${app.rag.retry-min-hits:2}") int retryMinHits,
            @Value("${app.rag.top-k:5}") int maxResults
    ) {
        if (retryMinHits <= 0) {
            throw new IllegalArgumentException("app.rag.retry-min-hits 必须大于 0");
        }
        if (maxResults <= 0) {
            throw new IllegalArgumentException("app.rag.top-k 必须大于 0");
        }
        this.retrievalService = retrievalService;
        this.questionRewriteService = questionRewriteService;
        this.retryMinHits = retryMinHits;
        this.maxResults = maxResults;
    }

    public TwoStageRetrievalResult retrieve(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("检索问题不能为空");
        }

        String originalQuestion = question.strip();
        List<RetrievedChunk> firstResults = retrievalService.search(originalQuestion);
        if (firstResults.size() >= retryMinHits) {
            return result(originalQuestion, null, false, firstResults);
        }

        String rewrittenQuestion = questionRewriteService.rewrite(originalQuestion)
                .filter(rewritten -> !rewritten.equalsIgnoreCase(originalQuestion))
                .orElse(null);
        if (rewrittenQuestion == null) {
            return result(originalQuestion, null, false, firstResults);
        }

        List<RetrievedChunk> secondResults = retrievalService.search(rewrittenQuestion);
        List<RetrievedChunk> mergedResults = merge(firstResults, secondResults);
        return result(originalQuestion, rewrittenQuestion, true, mergedResults);
    }

    private TwoStageRetrievalResult result(
            String originalQuestion,
            String rewrittenQuestion,
            boolean secondSearchExecuted,
            List<RetrievedChunk> chunks
    ) {
        List<RetrievedChunk> limited = chunks.stream()
                .sorted(Comparator.comparingDouble(RetrievedChunk::similarity).reversed())
                .limit(maxResults)
                .toList();
        return new TwoStageRetrievalResult(
                originalQuestion,
                rewrittenQuestion,
                secondSearchExecuted,
                limited
        );
    }

    private List<RetrievedChunk> merge(
            List<RetrievedChunk> firstResults,
            List<RetrievedChunk> secondResults
    ) {
        Map<String, RetrievedChunk> uniqueChunks = new LinkedHashMap<>();
        List<RetrievedChunk> allResults = new ArrayList<>(firstResults);
        allResults.addAll(secondResults);

        for (RetrievedChunk chunk : allResults) {
            uniqueChunks.merge(
                    identityOf(chunk),
                    chunk,
                    (existing, candidate) -> candidate.similarity() > existing.similarity()
                            ? candidate
                            : existing
            );
        }
        return List.copyOf(uniqueChunks.values());
    }

    private String identityOf(RetrievedChunk chunk) {
        if (chunk.documentId() != null && chunk.chunkIndex() != null && chunk.chunkIndex() >= 0) {
            return chunk.documentId() + ":" + chunk.chunkIndex();
        }
        return chunk.documentName() + ":" + chunk.content();
    }
}
