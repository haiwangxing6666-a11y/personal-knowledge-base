package com.ithwx.personalknowledgebase;

import com.ithwx.personalknowledgebase.repository.DocumentRepository;
import com.ithwx.personalknowledgebase.service.KnowledgeIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class PersonalKnowledgeBaseApplicationTests {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private KnowledgeIngestionService knowledgeIngestionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @Test
    void contextLoads() {
        assertNotNull(documentRepository);
        assertNotNull(vectorStore);
        assertNotNull(embeddingModel);
        assertNotNull(knowledgeIngestionService);
    }

    @Test
    void shouldKeepDocumentAndInitializeVectorStoreTables() {
        assertEquals(1, tableCount("document"));
        assertEquals(1, tableCount("vector_store"));
    }

    private Integer tableCount(String tableName) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = ?
                """,
                Integer.class,
                tableName
        );
    }

}
