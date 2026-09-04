package com.ithwx.personalknowledgebase.dto;

import java.util.List;

public record AnswerSource(
        Long documentId,
        String documentName,
        String sourceType,
        String sourceUrl,
        List<Integer> chunkIndexes
) {
    public AnswerSource {
        chunkIndexes = List.copyOf(chunkIndexes);
    }
}
