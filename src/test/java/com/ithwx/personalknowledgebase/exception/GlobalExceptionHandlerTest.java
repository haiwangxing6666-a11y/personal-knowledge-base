package com.ithwx.personalknowledgebase.exception;

import com.ithwx.personalknowledgebase.controller.ChatController;
import com.ithwx.personalknowledgebase.controller.DocumentController;
import com.ithwx.personalknowledgebase.service.DocumentManagementService;
import com.ithwx.personalknowledgebase.service.RagAnswerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private RagAnswerService ragAnswerService;

    @Mock
    private DocumentManagementService documentManagementService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new ChatController(ragAnswerService),
                        new DocumentController(documentManagementService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnUnifiedValidationError() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("请求参数校验失败"))
                .andExpect(jsonPath("$.path").value("/api/chat"))
                .andExpect(jsonPath("$.fieldErrors.question").value("问题不能为空"));
    }

    @Test
    void shouldReturnUnifiedErrorForUnreadableRequestBody() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"))
                .andExpect(jsonPath("$.message").value("请求体格式不正确"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void shouldReturnNotFoundForMissingDocument() throws Exception {
        when(documentManagementService.update(99L, "资料", "正文"))
                .thenThrow(new ResourceNotFoundException("资料不存在：99"));

        mockMvc.perform(put("/api/documents/{id}", 99L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"资料","content":"正文"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("资料不存在：99"))
                .andExpect(jsonPath("$.path").value("/api/documents/99"));
    }

    @Test
    void shouldReturnBadRequestForIllegalArgument() throws Exception {
        when(ragAnswerService.answer("测试问题"))
                .thenThrow(new IllegalArgumentException("问题不合法"));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"测试问题"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("问题不合法"));
    }

    @Test
    void shouldReturnSafeFileReadError() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "broken.pdf",
                "application/pdf",
                new byte[]{1, 2, 3}
        );
        when(documentManagementService.upload(any()))
                .thenThrow(new IOException("PDF internal parser details"));

        mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_READ_ERROR"))
                .andExpect(jsonPath("$.message").value("文件读取失败，请检查文件内容"))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("parser details")
                        )
                ));
    }

    @Test
    void shouldHideInternalExceptionDetails() throws Exception {
        when(documentManagementService.list())
                .thenThrow(new IllegalStateException(
                        "database password=secret and internal stack details"
                ));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("服务器内部错误，请稍后重试"))
                .andExpect(jsonPath("$.path").value("/api/documents"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("password=secret")
                        )
                ));
    }
}
