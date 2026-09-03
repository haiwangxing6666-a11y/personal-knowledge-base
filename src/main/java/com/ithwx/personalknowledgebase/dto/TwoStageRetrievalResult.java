package com.ithwx.personalknowledgebase.dto;

import java.util.List;

public record TwoStageRetrievalResult(
        String originalQuestion,
        String rewrittenQuestion,
        boolean secondSearchExecuted,
        List<RetrievedChunk> chunks
) {
    public TwoStageRetrievalResult {
        chunks = List.copyOf(chunks);
    }
}
