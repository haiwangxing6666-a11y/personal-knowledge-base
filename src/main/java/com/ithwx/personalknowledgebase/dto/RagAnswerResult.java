package com.ithwx.personalknowledgebase.dto;

import java.util.List;

public record RagAnswerResult(
        String question,
        String answer,
        boolean refused,
        String rewrittenQuestion,
        boolean secondSearchExecuted,
        List<AnswerSource> sources
) {
    public RagAnswerResult {
        sources = List.copyOf(sources);
    }
}
