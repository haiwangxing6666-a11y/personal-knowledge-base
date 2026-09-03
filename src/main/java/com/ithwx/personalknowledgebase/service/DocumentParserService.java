package com.ithwx.personalknowledgebase.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class DocumentParserService {

    public String parse(MultipartFile file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String fileType = getFileType(file.getOriginalFilename());

        return switch (fileType) {
            case "txt", "md" -> parseText(file);
            case "pdf" -> parsePdf(file);
            case "docx" -> parseDocx(file);
            default -> throw new IllegalArgumentException("不支持的文件格式：" + fileType);
        };
    }

    private String getFileType(String filename) {
        String extension = StringUtils.getFilenameExtension(filename);
        if (!StringUtils.hasText(extension)) {
            throw new IllegalArgumentException("无法识别文件格式：" + filename);
        }
        return extension.toLowerCase(Locale.ROOT);
    }

    private String parseText(MultipartFile file) throws IOException {
        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }

    private String parsePdf(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private String parseDocx(MultipartFile file) throws IOException {
        StringBuilder content = new StringBuilder();

        try (InputStream inputStream = file.getInputStream();
             XWPFDocument document = new XWPFDocument(inputStream)) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                content.append(paragraph.getText()).append(System.lineSeparator());
            }
        }

        return content.toString();
    }
}
