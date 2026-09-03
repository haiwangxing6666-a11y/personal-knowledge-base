package com.ithwx.personalknowledgebase.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChunkingService {

    private static final String PARAGRAPH_SEPARATOR = "\n\n";

    private final int maxChars;
    private final int overlapChars;

    public ChunkingService(
            @Value("${app.chunk.max-chars:500}") int maxChars,
            @Value("${app.chunk.overlap-chars:50}") int overlapChars
    ) {
        if (maxChars <= 0) {
            throw new IllegalArgumentException("app.chunk.max-chars 必须大于 0");
        }
        if (overlapChars < 0 || overlapChars >= maxChars) {
            throw new IllegalArgumentException(
                    "app.chunk.overlap-chars 必须大于等于 0 且小于 max-chars"
            );
        }
        this.maxChars = maxChars;
        this.overlapChars = overlapChars;
    }

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalizedText = normalizeParagraphs(text);
        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < normalizedText.length()) {
            int maxEnd = Math.min(start + maxChars, normalizedText.length());
            int end = findPreferredEnd(normalizedText, start, maxEnd);

            String chunk = normalizedText.substring(start, end).strip();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            if (end == normalizedText.length()) {
                break;
            }

            start = end - overlapChars;
        }

        return List.copyOf(chunks);
    }

    private String normalizeParagraphs(String text) {
        return Arrays.stream(text.strip().split("\\R\\s*\\R"))
                .map(String::strip)
                .filter(paragraph -> !paragraph.isEmpty())
                .collect(Collectors.joining(PARAGRAPH_SEPARATOR));
    }

    private int findPreferredEnd(String text, int start, int maxEnd) {
        if (maxEnd == text.length()) {
            return maxEnd;
        }

        int paragraphEnd = text.lastIndexOf(PARAGRAPH_SEPARATOR, maxEnd - 1);
        int minimumUsefulEnd = start + overlapChars;

        if (paragraphEnd > minimumUsefulEnd) {
            return paragraphEnd;
        }

        return maxEnd;
    }
}
