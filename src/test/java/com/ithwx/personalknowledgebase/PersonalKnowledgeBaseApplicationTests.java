package com.ithwx.personalknowledgebase;

import com.ithwx.personalknowledgebase.repository.DocumentRepository;
import com.ithwx.personalknowledgebase.service.DocumentManagementService;
import com.ithwx.personalknowledgebase.service.KnowledgeIngestionService;
import com.ithwx.personalknowledgebase.service.RagRetrievalService;
import com.ithwx.personalknowledgebase.service.QuestionRewriteService;
import com.ithwx.personalknowledgebase.service.TwoStageRetrievalService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
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
    private DocumentManagementService documentManagementService;

    @Autowired
    private RagRetrievalService ragRetrievalService;

    @Autowired
    private QuestionRewriteService questionRewriteService;

    @Autowired
    private TwoStageRetrievalService twoStageRetrievalService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @MockitoBean
    private ChatModel chatModel;

    @Test
    void contextLoads() {
        assertNotNull(documentRepository);
        assertNotNull(vectorStore);
        assertNotNull(embeddingModel);
        assertNotNull(knowledgeIngestionService);
        assertNotNull(documentManagementService);
        assertNotNull(ragRetrievalService);
        assertNotNull(questionRewriteService);
        assertNotNull(twoStageRetrievalService);
        assertNotNull(chatModel);
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
