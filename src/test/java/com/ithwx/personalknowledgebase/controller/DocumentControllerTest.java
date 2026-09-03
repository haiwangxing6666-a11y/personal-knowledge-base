package com.ithwx.personalknowledgebase.controller;

import com.ithwx.personalknowledgebase.entity.DocumentEntity;
import com.ithwx.personalknowledgebase.service.DocumentManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DocumentControllerTest {

    @Mock
    private DocumentManagementService documentManagementService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DocumentController(documentManagementService))
                .build();
    }

    @Test
    void shouldUploadFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "资料.TXT",
                "text/plain",
                "中文正文".getBytes(StandardCharsets.UTF_8)
        );
        when(documentManagementService.upload(file))
                .thenReturn(document(1L, "资料.TXT", "txt", null));

        mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("资料.TXT"))
                .andExpect(jsonPath("$.fileType").value("txt"))
                .andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    void shouldCreateNote() throws Exception {
        when(documentManagementService.createNote("学习笔记", "笔记正文"))
                .thenReturn(document(2L, "学习笔记", "note", null));

        mockMvc.perform(post("/api/documents/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"学习笔记","content":"笔记正文"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.fileType").value("note"));
    }

    @Test
    void shouldRejectBlankNote() throws Exception {
        mockMvc.perform(post("/api/documents/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":" ","content":" "}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(documentManagementService);
    }

    @Test
    void shouldCreateWebLink() throws Exception {
        String url = "https://example.com/article";
        when(documentManagementService.createLink(url, null))
                .thenReturn(document(3L, "网页标题", "web", url));

        mockMvc.perform(post("/api/documents/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://example.com/article"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.sourceUrl").value(url));

        verify(documentManagementService).createLink(url, null);
    }

    @Test
    void shouldListDocuments() throws Exception {
        when(documentManagementService.list()).thenReturn(List.of(
                document(2L, "学习笔记", "note", null),
                document(1L, "资料.txt", "txt", null)
        ));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("学习笔记"))
                .andExpect(jsonPath("$[1].name").value("资料.txt"));
    }

    private DocumentEntity document(
            Long id,
            String name,
            String fileType,
            String sourceUrl
    ) {
        DocumentEntity entity = new DocumentEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setFileType(fileType);
        entity.setSourceUrl(sourceUrl);
        entity.setStatus("READY");
        entity.setChunkCount(1);
        entity.setUploadTime(LocalDateTime.of(2026, 9, 3, 12, 0));
        return entity;
    }
}
