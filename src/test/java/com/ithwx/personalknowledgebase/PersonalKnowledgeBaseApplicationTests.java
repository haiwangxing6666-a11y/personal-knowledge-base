package com.ithwx.personalknowledgebase;

import com.ithwx.personalknowledgebase.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class PersonalKnowledgeBaseApplicationTests {

    @Autowired
    private DocumentRepository documentRepository;

    @Test
    void contextLoads() {
        assertNotNull(documentRepository);
    }

}
