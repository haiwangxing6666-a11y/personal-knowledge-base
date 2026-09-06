package com.ithwx.personalknowledgebase;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticResourceTest {

    @Test
    void shouldProvideStylesAndDocumentManagementScript() throws Exception {
        String css = read("static/css/app.css");
        String page = read("static/index.html");
        String script = read("static/js/app.js");

        assertTrue(css.contains("@media (max-width: 600px)"));
        assertTrue(css.contains(".library-state[hidden]"));
        assertTrue(script.contains("/api/documents/notes"));
        assertTrue(script.contains("/api/documents/links"));
        assertTrue(script.contains("method: \"DELETE\""));
        assertTrue(script.contains("method: \"PUT\""));
        assertTrue(script.contains("openNoteEditor"));
        assertTrue(script.contains("openFileReplacement"));
        assertTrue(page.contains("id=\"edit-note-dialog\""));
        assertTrue(page.contains("id=\"replace-file-dialog\""));
        assertTrue(page.contains("accept=\".txt,.md,.markdown,.pdf,.docx\""));
        assertTrue(script.contains("fieldErrors"));
    }

    @Test
    void shouldProvideKnowledgeChatPageAndScript() throws Exception {
        String page = read("static/chat.html");
        String script = read("static/js/chat.js");

        assertTrue(page.contains("向你的知识岛提问"));
        assertTrue(page.contains("/js/chat.js"));
        assertTrue(page.contains("请替换【】中的内容"));
        assertTrue(page.contains("【资料中的具体概念】是什么？"));
        assertTrue(page.contains(">【资料中的具体概念】是什么？</button>"));
        assertTrue(page.contains(">【概念 A】和【概念 B】有什么区别？</button>"));
        assertTrue(page.contains(">根据资料说明【一个具体问题】</button>"));
        assertFalse(page.contains("总结知识库的主要内容"));
        assertTrue(script.contains("/api/chat"));
        assertTrue(script.contains("secondSearchExecuted"));
        assertTrue(script.contains("sourceUrl"));
        assertTrue(script.contains("event.shiftKey"));
    }

    private String read(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        assertTrue(resource.exists());
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }
}
