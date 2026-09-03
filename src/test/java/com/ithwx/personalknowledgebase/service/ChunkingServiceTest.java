package com.ithwx.personalknowledgebase.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkingServiceTest {

    private final ChunkingService service = new ChunkingService(10, 3);

    @Test
    void shouldReturnEmptyListForEmptyText() {
        assertTrue(service.chunk(null).isEmpty());
        assertTrue(service.chunk("").isEmpty());
        assertTrue(service.chunk("  \n  ").isEmpty());
    }

    @Test
    void shouldPreferParagraphBoundaries() {
        List<String> chunks = service.chunk("12345\n\n67890\n\nabc");

        assertEquals(List.of("12345", "345\n\n67890", "890\n\nabc"), chunks);
    }

    @Test
    void shouldKeepOverlapBetweenAdjacentChunks() {
        List<String> chunks = service.chunk("12345\n\n67890\n\nabc");

        for (int index = 1; index < chunks.size(); index++) {
            String previous = chunks.get(index - 1);
            String current = chunks.get(index);
            assertEquals(
                    previous.substring(previous.length() - 3),
                    current.substring(0, 3)
            );
        }
    }

    @Test
    void shouldHardSplitAnOversizedParagraph() {
        List<String> chunks = service.chunk("abcdefghijklmnop");

        assertEquals(List.of("abcdefghij", "hijklmnop"), chunks);
    }

    @Test
    void shouldNeverExceedConfiguredMaximumLength() {
        List<String> chunks = service.chunk(
                "12345\n\n67890\n\nabcdefghijklmnop\n\nxyz"
        );

        assertTrue(chunks.stream().allMatch(chunk -> chunk.length() <= 10));
    }

    @Test
    void shouldRejectInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new ChunkingService(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ChunkingService(10, 10));
    }
}
