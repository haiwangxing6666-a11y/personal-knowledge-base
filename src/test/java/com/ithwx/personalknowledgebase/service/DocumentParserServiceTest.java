package com.ithwx.personalknowledgebase.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentParserServiceTest {

    private final DocumentParserService parserService = new DocumentParserService();

    @Test
    void shouldParseChineseTxtFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "note.txt",
                "text/plain",
                "实验室知识库测试内容".getBytes(StandardCharsets.UTF_8)
        );

        assertEquals("实验室知识库测试内容", parserService.parse(file));
    }

    @Test
    void shouldParseMarkdownFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "README.md",
                "text/markdown",
                "# 项目说明\n\n这是 Markdown 内容。".getBytes(StandardCharsets.UTF_8)
        );

        assertEquals("# 项目说明\n\n这是 Markdown 内容。", parserService.parse(file));
    }

    @Test
    void shouldParsePdfFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                "application/pdf",
                createPdf("PDF parsing works")
        );

        assertTrue(parserService.parse(file).contains("PDF parsing works"));
    }

    @Test
    void shouldParseDocxFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                createDocx("DOCX 中文解析测试")
        );

        assertTrue(parserService.parse(file).contains("DOCX 中文解析测试"));
    }

    @Test
    void shouldRecognizeUppercaseFileExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "note.TXT",
                "text/plain",
                "大写扩展名测试".getBytes(StandardCharsets.UTF_8)
        );

        assertEquals("大写扩展名测试", parserService.parse(file));
    }

    @Test
    void shouldRejectUnsupportedFileType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3}
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> parserService.parse(file)
        );

        assertEquals("不支持的文件格式：jpg", exception.getMessage());
    }

    private byte[] createPdf(String text) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }

            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] createDocx(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
            return output.toByteArray();
        }
    }
}
