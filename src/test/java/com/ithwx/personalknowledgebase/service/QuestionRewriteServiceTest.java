package com.ithwx.personalknowledgebase.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionRewriteServiceTest {

    @Mock
    private ChatModel chatModel;

    @Test
    void shouldRewriteAndNormalizeQuestion() {
        QuestionRewriteService service = new QuestionRewriteService(chatModel);
        when(chatModel.call(contains("它支持什么格式")))
                .thenReturn("“个人知识库支持哪些文档格式？”");

        assertEquals(
                "个人知识库支持哪些文档格式？",
                service.rewrite("  它支持什么格式  ").orElseThrow()
        );
    }

    @Test
    void shouldReturnEmptyWhenModelFailsOrReturnsBlank() {
        QuestionRewriteService service = new QuestionRewriteService(chatModel);
        when(chatModel.call(contains("第一个问题")))
                .thenThrow(new IllegalStateException("模型暂时不可用"));
        when(chatModel.call(contains("第二个问题"))).thenReturn("  ");

        assertTrue(service.rewrite("第一个问题").isEmpty());
        assertTrue(service.rewrite("第二个问题").isEmpty());
    }

    @Test
    void shouldRejectBlankQuestion() {
        QuestionRewriteService service = new QuestionRewriteService(chatModel);

        assertThrows(IllegalArgumentException.class, () -> service.rewrite("  "));
    }
}
