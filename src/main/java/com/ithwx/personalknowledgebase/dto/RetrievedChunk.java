package com.ithwx.personalknowledgebase.dto;

public record RetrievedChunk(
        String content,
        Long documentId,
        String documentName,
        String sourceType,
        String sourceUrl,
        Integer chunkIndex,
        double similarity
) {
}
