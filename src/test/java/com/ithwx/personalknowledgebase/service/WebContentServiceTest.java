package com.ithwx.personalknowledgebase.service;

import com.ithwx.personalknowledgebase.dto.WebPage;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebContentServiceTest {

    private final WebContentService service = new WebContentService(1024, 5, 10);

    @Test
    void shouldExtractChineseTitleAndMainText() {
        String html = """
                <html>
                  <head>
                    <title>中文网页标题</title>
                    <style>.hidden { display: none; }</style>
                  </head>
                  <body>
                    <nav>导航菜单</nav>
                    <main>
                      <h1>知识库正文</h1>
                      <p>这是需要保存的中文内容。</p>
                    </main>
                    <script>console.log('脚本内容');</script>
                  </body>
                </html>
                """;

        WebPage result = service.parseContent(
                URI.create("https://example.com/article"),
                "text/html; charset=UTF-8",
                html.getBytes(StandardCharsets.UTF_8)
        );

        assertEquals("中文网页标题", result.title());
        assertEquals("知识库正文 这是需要保存的中文内容。", result.text());
        assertFalse(result.text().contains("导航菜单"));
        assertFalse(result.text().contains("脚本内容"));
    }

    @Test
    void shouldExtractPlainText() {
        WebPage result = service.parseContent(
                URI.create("https://example.com/note.txt"),
                "text/plain; charset=UTF-8",
                "  纯文本网页内容  ".getBytes(StandardCharsets.UTF_8)
        );

        assertEquals("example.com", result.title());
        assertEquals("纯文本网页内容", result.text());
    }

    @Test
    void shouldRejectBlankUrl() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.fetch("  ")
        );

        assertEquals("网页地址不能为空", exception.getMessage());
    }

    @Test
    void shouldRejectFileProtocol() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.fetch("file:///C:/Windows/System32/test.txt")
        );

        assertEquals("只支持公开的 http/https 网址", exception.getMessage());
    }

    @Test
    void shouldRejectLoopbackAddress() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.fetch("http://127.0.0.1:8080/private")
        );

        assertEquals("不允许访问本机或内网地址", exception.getMessage());
    }

    @Test
    void shouldRejectPrivateNetworkAddress() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.fetch("http://192.168.1.1/admin")
        );

        assertEquals("不允许访问本机或内网地址", exception.getMessage());
    }

    @Test
    void shouldRejectUnsupportedContentType() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.parseContent(
                        URI.create("https://example.com/image.png"),
                        "image/png",
                        new byte[]{1, 2, 3}
                )
        );

        assertEquals("网页内容类型不受支持：image/png", exception.getMessage());
    }

    @Test
    void shouldRejectHtmlWithoutText() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.parseContent(
                        URI.create("https://example.com/empty"),
                        "text/html",
                        "<html><script>only script</script></html>".getBytes(StandardCharsets.UTF_8)
                )
        );

        assertEquals("网页中没有可提取的正文", exception.getMessage());
    }
}
