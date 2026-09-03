package com.ithwx.personalknowledgebase.dto;

import com.ithwx.personalknowledgebase.entity.DocumentEntity;

import java.time.LocalDateTime;

public record DocumentResponse(
        Long id,
        String name,
        String fileType,
        String sourceUrl,
        String status,
        Integer chunkCount,
        LocalDateTime uploadTime
) {

    public static DocumentResponse from(DocumentEntity entity) {
        return new DocumentResponse(
                entity.getId(),
                entity.getName(),
                entity.getFileType(),
                entity.getSourceUrl(),
                entity.getStatus(),
                entity.getChunkCount(),
                entity.getUploadTime()
        );
    }
}
