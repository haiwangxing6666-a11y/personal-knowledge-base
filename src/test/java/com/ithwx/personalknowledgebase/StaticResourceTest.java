package com.ithwx.personalknowledgebase;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticResourceTest {

    @Test
    void shouldProvideStylesAndDocumentManagementScript() throws Exception {
        String css = read("static/css/app.css");
        String script = read("static/js/app.js");

        assertTrue(css.contains("@media (max-width: 600px)"));
        assertTrue(script.contains("/api/documents/notes"));
        assertTrue(script.contains("/api/documents/links"));
        assertTrue(script.contains("method: \"DELETE\""));
        assertTrue(script.contains("fieldErrors"));
    }

    @Test
    void shouldProvideKnowledgeChatPageAndScript() throws Exception {
        String page = read("static/chat.html");
        String script = read("static/js/chat.js");

        assertTrue(page.contains("向你的知识岛提问"));
        assertTrue(page.contains("/js/chat.js"));
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
