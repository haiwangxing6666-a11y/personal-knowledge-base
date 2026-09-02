package com.ithwx.personalknowledgebase.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "document")
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "file_path", length = 1024)
    private String filePath;

    @Column(name = "file_type", length = 32)
    private String fileType;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "upload_time", nullable = false)
    private LocalDateTime uploadTime;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "chunk_count", nullable = false)
    private Integer chunkCount;

    @PrePersist
    void initializeDefaults() {
        if (uploadTime == null) {
            uploadTime = LocalDateTime.now();
        }
        if (status == null) {
            status = "PENDING";
        }
        if (chunkCount == null) {
            chunkCount = 0;
        }
    }
}
